import os
import uuid
import asyncio
import re
from pathlib import Path
from typing import Optional, List, Dict, Any
from fastapi import FastAPI, Request, Form, HTTPException, BackgroundTasks
from fastapi.responses import HTMLResponse, FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
import yt_dlp
from pydantic import BaseModel

app = FastAPI(title="AnMusic Downloader", version="2.0.0")

BASE_DIR = Path(__file__).resolve().parent
DOWNLOAD_DIR = BASE_DIR / "downloads"
DOWNLOAD_DIR.mkdir(exist_ok=True)

app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")
templates = Jinja2Templates(directory=BASE_DIR / "templates")

active_downloads = {}


class DownloadRequest(BaseModel):
    url: str
    format_type: str = "video"
    quality: str = "best"


def get_platform_from_url(url: str) -> str:
    """Detect platform from URL."""
    url_lower = url.lower()
    if 'youtube.com' in url_lower or 'youtu.be' in url_lower:
        return 'youtube'
    elif 'instagram.com' in url_lower:
        return 'instagram'
    elif 'tiktok.com' in url_lower:
        return 'tiktok'
    elif 'twitter.com' in url_lower or 'x.com' in url_lower:
        return 'twitter'
    elif 'facebook.com' in url_lower or 'fb.watch' in url_lower:
        return 'facebook'
    elif 'vimeo.com' in url_lower:
        return 'vimeo'
    elif 'soundcloud.com' in url_lower:
        return 'soundcloud'
    elif 'reddit.com' in url_lower:
        return 'reddit'
    elif 'twitch.tv' in url_lower:
        return 'twitch'
    elif 'dailymotion.com' in url_lower:
        return 'dailymotion'
    elif 'bilibili.com' in url_lower:
        return 'bilibili'
    else:
        return 'unknown'


def get_ydl_opts(format_type: str, quality: str, output_path: str, platform: str = 'unknown'):
    """Configure yt-dlp options based on format, quality and platform."""
    base_opts = {
        'outtmpl': output_path,
        'noplaylist': True,
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        'ignoreerrors': False,
        'retries': 3,
        'fragment_retries': 3,
        'extractor_retries': 3,
        'socket_timeout': 30,
        'http_headers': {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'en-us,en;q=0.5',
            'Sec-Fetch-Mode': 'navigate',
        },
    }

    if platform == 'instagram':
        base_opts['extractor_args'] = {'instagram': {'api': 'graphql'}}
    elif platform == 'twitter':
        base_opts['extractor_args'] = {'twitter': {'api': 'graphql'}}
    
    if format_type == "audio":
        base_opts.update({
            'format': 'bestaudio/best',
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '320',
            }],
        })
    else:
        if quality == "4k":
            base_opts['format'] = 'bestvideo[height<=2160]+bestaudio/best[height<=2160]/best'
        elif quality == "1440p":
            base_opts['format'] = 'bestvideo[height<=1440]+bestaudio/best[height<=1440]/best'
        elif quality == "1080p":
            base_opts['format'] = 'bestvideo[height<=1080]+bestaudio/best[height<=1080]/best'
        elif quality == "720p":
            base_opts['format'] = 'bestvideo[height<=720]+bestaudio/best[height<=720]/best'
        elif quality == "480p":
            base_opts['format'] = 'bestvideo[height<=480]+bestaudio/best[height<=480]/best'
        elif quality == "360p":
            base_opts['format'] = 'bestvideo[height<=360]+bestaudio/best[height<=360]/best'
        elif quality == "best":
            base_opts['format'] = 'bestvideo+bestaudio/best'
        else:
            base_opts['format'] = 'bestvideo+bestaudio/best'
        
        base_opts['merge_output_format'] = 'mp4'
    
    return base_opts


async def download_video(download_id: str, url: str, format_type: str, quality: str):
    """Background task to download video."""
    try:
        active_downloads[download_id] = {"status": "downloading", "progress": 0, "filename": None, "title": None}
        
        output_template = str(DOWNLOAD_DIR / f"{download_id}_%(title).100s.%(ext)s")
        platform = get_platform_from_url(url)
        ydl_opts = get_ydl_opts(format_type, quality, output_template, platform)
        
        def progress_hook(d):
            if d['status'] == 'downloading':
                total = d.get('total_bytes') or d.get('total_bytes_estimate', 0)
                downloaded = d.get('downloaded_bytes', 0)
                if total > 0:
                    active_downloads[download_id]["progress"] = int((downloaded / total) * 100)
                elif d.get('downloaded_bytes'):
                    active_downloads[download_id]["progress"] = min(99, active_downloads[download_id].get("progress", 0) + 1)
            elif d['status'] == 'finished':
                active_downloads[download_id]["progress"] = 100
                fname = d.get('filename')
                if fname:
                    active_downloads[download_id]["filename"] = Path(fname).name
                info = d.get('info_dict', {})
                if info.get('title'):
                    active_downloads[download_id]["title"] = info['title']
        
        ydl_opts['progress_hooks'] = [progress_hook]
        
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, lambda: _download_sync(url, ydl_opts))
        
        files = list(DOWNLOAD_DIR.glob(f"{download_id}_*"))
        if files:
            active_downloads[download_id]["status"] = "completed"
            active_downloads[download_id]["progress"] = 100
            active_downloads[download_id]["filename"] = files[0].name
        else:
            active_downloads[download_id]["status"] = "failed"
            active_downloads[download_id]["error"] = "File not found after download"
            
    except Exception as e:
        active_downloads[download_id] = {"status": "failed", "error": str(e), "progress": 0}


def _download_sync(url: str, ydl_opts: dict):
    """Synchronous download function."""
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([url])


@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    html_path = BASE_DIR / "templates" / "index.html"
    with open(html_path, "r", encoding="utf-8") as f:
        html_content = f.read()
    return HTMLResponse(content=html_content)


@app.post("/api/download")
async def start_download(
    background_tasks: BackgroundTasks,
    url: str = Form(...),
    format_type: str = Form("video"),
    quality: str = Form("best")
):
    download_id = str(uuid.uuid4())[:8]
    background_tasks.add_task(download_video, download_id, url, format_type, quality)
    return {"download_id": download_id, "status": "started"}


@app.get("/api/status/{download_id}")
async def get_status(download_id: str):
    if download_id not in active_downloads:
        raise HTTPException(status_code=404, detail="Download not found")
    return active_downloads[download_id]


@app.get("/api/download-file/{download_id}")
async def download_file(download_id: str):
    if download_id not in active_downloads:
        raise HTTPException(status_code=404, detail="Download not found")
    
    download = active_downloads[download_id]
    if download.get("status") != "completed" or not download.get("filename"):
        raise HTTPException(status_code=400, detail="Download not ready")
    
    file_path = DOWNLOAD_DIR / download["filename"]
    if not file_path.exists():
        raise HTTPException(status_code=404, detail="File not found")
    
    return FileResponse(
        path=file_path,
        filename=download["filename"],
        media_type='application/octet-stream'
    )


def extract_all_video_qualities(formats: List[Dict]) -> List[Dict]:
    """Extract all unique video qualities from formats."""
    qualities = {}
    for f in formats:
        height = f.get('height')
        vcodec = f.get('vcodec', 'none')
        if height and vcodec != 'none' and isinstance(height, int):
            h = height
            ext = f.get('ext', 'mp4')
            filesize = f.get('filesize') or f.get('filesize_approx')
            fps = f.get('fps')
            vcodec_name = f.get('vcodec', 'unknown')
            
            if h not in qualities:
                qualities[h] = {
                    'height': h,
                    'label': f'{h}p',
                    'ext': ext,
                    'vcodec': vcodec_name,
                    'filesize': filesize,
                    'fps': fps,
                    'format_id': f.get('format_id'),
                }
            elif filesize and (not qualities[h].get('filesize') or filesize < qualities[h].get('filesize', float('inf'))):
                qualities[h]['filesize'] = filesize
                qualities[h]['vcodec'] = vcodec_name
    
    sorted_qualities = sorted(qualities.values(), key=lambda x: x['height'], reverse=True)
    
    for q in sorted_qualities:
        if q['height'] >= 2160:
            q['label'] = '4K (2160p)'
        elif q['height'] >= 1440:
            q['label'] = '1440p (2K)'
        elif q['height'] >= 1080:
            q['label'] = '1080p (Full HD)'
        elif q['height'] >= 720:
            q['label'] = '720p (HD)'
        elif q['height'] >= 480:
            q['label'] = '480p (SD)'
        elif q['height'] >= 360:
            q['label'] = '360p'
        else:
            q['label'] = f"{q['height']}p"
    
    return sorted_qualities


def extract_all_audio_qualities(formats: List[Dict]) -> List[Dict]:
    """Extract all unique audio qualities from formats."""
    qualities = {}
    for f in formats:
        acodec = f.get('acodec', 'none')
        vcodec = f.get('vcodec', 'none')
        abr = f.get('abr') or f.get('tbr')
        
        if acodec != 'none' and vcodec == 'none' and abr:
            try:
                q = int(float(abr))
                ext = f.get('ext', 'mp3')
                filesize = f.get('filesize') or f.get('filesize_approx')
                acodec_name = f.get('acodec', 'unknown')
                
                if q not in qualities:
                    qualities[q] = {
                        'abr': q,
                        'label': f'{q} kbps',
                        'ext': ext,
                        'acodec': acodec_name,
                        'filesize': filesize,
                        'format_id': f.get('format_id'),
                    }
                elif filesize and (not qualities[q].get('filesize') or filesize < qualities[q].get('filesize', float('inf'))):
                    qualities[q]['filesize'] = filesize
                    qualities[q]['acodec'] = acodec_name
            except:
                pass
    
    sorted_qualities = sorted(qualities.values(), key=lambda x: x['abr'], reverse=True)
    
    for q in sorted_qualities:
        if q['abr'] >= 320:
            q['label'] = '320 kbps (High Quality)'
        elif q['abr'] >= 256:
            q['label'] = '256 kbps'
        elif q['abr'] >= 192:
            q['label'] = '192 kbps'
        elif q['abr'] >= 128:
            q['label'] = '128 kbps'
        else:
            q['label'] = f"{q['abr']} kbps"
    
    return sorted_qualities


@app.get("/api/info")
async def get_video_info(url: str):
    """Get video info without downloading - supports all platforms."""
    try:
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
            'noplaylist': True,
            'ignoreerrors': False,
            'extractor_args': {
                'youtube': {'skip': []},
                'instagram': {'api': 'graphql'},
                'twitter': {'api': 'graphql'},
            },
            'http_headers': {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            },
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            video_qualities = extract_all_video_qualities(info.get("formats", []))
            audio_qualities = extract_all_audio_qualities(info.get("formats", []))
            
            platform = get_platform_from_url(url)
            
            return {
                "title": info.get("title"),
                "duration": info.get("duration"),
                "thumbnail": info.get("thumbnail"),
                "uploader": info.get("uploader"),
                "platform": platform,
                "video_qualities": video_qualities,
                "audio_qualities": audio_qualities,
                "formats": [
                    {
                        "format_id": f.get("format_id"),
                        "ext": f.get("ext"),
                        "resolution": f.get("resolution"),
                        "filesize": f.get("filesize"),
                        "vcodec": f.get("vcodec"),
                        "acodec": f.get("acodec"),
                        "height": f.get("height"),
                        "abr": f.get("abr"),
                        "fps": f.get("fps"),
                    }
                    for f in info.get("formats", [])
                ]
            }
    except yt_dlp.utils.DownloadError as e:
        raise HTTPException(status_code=400, detail=f"Unsupported URL or video unavailable: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/api/platforms")
async def get_supported_platforms():
    """Return list of supported platforms."""
    return {
        "platforms": [
            {"name": "YouTube", "domains": ["youtube.com", "youtu.be", "youtube.com/shorts"], "icon": "bi-youtube"},
            {"name": "YouTube Music", "domains": ["music.youtube.com"], "icon": "bi-music-note"},
            {"name": "Instagram", "domains": ["instagram.com", "instagr.am"], "icon": "bi-instagram"},
            {"name": "TikTok", "domains": ["tiktok.com", "vm.tiktok.com"], "icon": "bi-tiktok"},
            {"name": "Twitter / X", "domains": ["twitter.com", "x.com", "t.co"], "icon": "bi-twitter-x"},
            {"name": "Facebook", "domains": ["facebook.com", "fb.watch", "m.facebook.com"], "icon": "bi-facebook"},
            {"name": "Vimeo", "domains": ["vimeo.com"], "icon": "bi-vimeo"},
            {"name": "SoundCloud", "domains": ["soundcloud.com", "snd.sc"], "icon": "bi-soundcloud"},
            {"name": "Reddit", "domains": ["reddit.com", "v.redd.it"], "icon": "bi-reddit"},
            {"name": "Twitch", "domains": ["twitch.tv", "clips.twitch.tv"], "icon": "bi-twitch"},
            {"name": "Dailymotion", "domains": ["dailymotion.com"], "icon": "bi-camera-video"},
            {"name": "Bilibili", "domains": ["bilibili.com", "b23.tv"], "icon": "bi-camera-video"},
            {"name": "And 1000+ more sites...", "domains": [], "icon": "bi-globe"},
        ]
    }


if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)