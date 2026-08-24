import os
import uuid
import requests
from pathlib import Path
from typing import Any, Dict, Optional
from fastapi import FastAPI, Request, Form, HTTPException, BackgroundTasks
from fastapi.responses import HTMLResponse, FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

app = FastAPI(title="AnMusic Downloader", version="7.0.0")

BASE_DIR = Path(__file__).resolve().parent
DOWNLOAD_DIR = BASE_DIR / "downloads"
DOWNLOAD_DIR.mkdir(exist_ok=True)

app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")
templates = Jinja2Templates(directory=BASE_DIR / "templates")

active_downloads = {}

# RapidAPI Settings (For Instagram & Facebook)
RAPIDAPI_KEY = "ed12f7558bmsh321cb0f7580ccb0p1566d4jsne2c937cd594e"
RAPIDAPI_HOST = "social-download-all-in-one.p.rapidapi.com"

def fetch_rapidapi(url: str, format_type: str = "video"):
    """इंस्टाग्राम और फेसबुक के लिए"""
    api_url = f"https://{RAPIDAPI_HOST}/v1/social/autolink"
    headers = {
        "x-rapidapi-key": RAPIDAPI_KEY,
        "x-rapidapi-host": RAPIDAPI_HOST,
        "Content-Type": "application/json"
    }
    try:
        response = requests.post(api_url, json={"url": url}, headers=headers, timeout=25)
        data = response.json()
        
        if "medias" in data and isinstance(data["medias"], list):
            for item in data["medias"]:
                if format_type == "audio" and item.get("type") == "audio":
                    return item.get("url")
                if format_type == "video" and item.get("type") == "video":
                    return item.get("url")
            if data["medias"]: return data["medias"][0].get("url")
            
        for key in ["url", "download_url", "link"]:
            if key in data: return data[key]
    except Exception as e:
        print(f"RapidAPI Error: {e}")
    return None

def fetch_youtube_api(url: str, format_type: str = "video", quality: str = "720"):
    """यूट्यूब के लिए बाईपास तरीका (403 Error से बचने के लिए)"""
    cobalt_url = "https://api.cobalt.tools/api/json"
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Origin": "https://cobalt.tools",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
    }
    payload = {
        "url": url,
        "vQuality": quality if format_type == "video" else "720",
        "isAudioOnly": True if format_type == "audio" else False,
        "aFormat": "mp3"
    }
    # 1st Try: Cobalt API
    try:
        res = requests.post(cobalt_url, json=payload, headers=headers, timeout=15)
        if res.status_code == 200:
            data = res.json()
            if data.get("status") in ["redirect", "stream", "picker"]:
                return data.get("url")
    except:
        pass
    
    # 2nd Try: Backup API
    wuk_url = "https://co.wuk.sh/api/json"
    try:
        res = requests.post(wuk_url, json=payload, headers=headers, timeout=15)
        if res.status_code == 200:
            data = res.json()
            if data.get("status") in ["redirect", "stream", "picker"]:
                return data.get("url")
    except:
        pass
    return None

def download_file_background(download_id: str, direct_url: str, ext: str):
    try:
        active_downloads[download_id] = {"status": "downloading", "progress": 0, "filename": None, "title": f"AnMusic_{download_id}", "ext": ext}
        filename = f"{download_id}.{ext}"
        filepath = DOWNLOAD_DIR / filename
        
        headers = {
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
            "Accept": "*/*"
        }
        
        response = requests.get(direct_url, headers=headers, stream=True, timeout=30)
        response.raise_for_status()
        
        total_size = int(response.headers.get('content-length', 0))
        downloaded = 0
        
        with open(filepath, 'wb') as f:
            for data in response.iter_content(chunk_size=1024 * 1024):
                if data:
                    f.write(data)
                    downloaded += len(data)
                    if total_size > 0:
                        active_downloads[download_id]["progress"] = int((downloaded / total_size) * 100)
                        
        active_downloads[download_id].update({"status": "completed", "progress": 100, "filename": filename})
    except Exception as e:
        active_downloads[download_id] = {"status": "failed", "error": f"Error: {str(e)}", "progress": 0}

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    with open(BASE_DIR / "templates" / "index.html", "r", encoding="utf-8") as f:
        return HTMLResponse(content=f.read())

@app.get("/api/info")
async def get_video_info(url: str):
    """यहाँ से आपकी वेबसाइट को पता चलेगा कि कौन सी क्वालिटी दिखानी है"""
    url_lower = url.lower()
    
    if "youtube.com" in url_lower or "youtu.be" in url_lower:
        formats = [
            {"format_id": "1080", "ext": "mp4", "format_note": "1080p Full HD", "resolution": "1080p"},
            {"format_id": "720", "ext": "mp4", "format_note": "720p HD", "resolution": "720p"},
            {"format_id": "360", "ext": "mp4", "format_note": "360p SD", "resolution": "360p"},
            {"format_id": "audio", "ext": "mp3", "format_note": "Audio (MP3)", "resolution": "MP3"}
        ]
    else:
        formats = [
            {"format_id": "hd", "ext": "mp4", "format_note": "Best Video Quality", "resolution": "HD"},
            {"format_id": "audio", "ext": "mp3", "format_note": "Audio (MP3)", "resolution": "MP3"}
        ]
        
    return {
        "title": "Ready to Download",
        "thumbnail": "https://img.freepik.com/free-vector/video-player-interface-design_1017-33336.jpg",
        "duration": 0,
        "formats": formats
    }

@app.post("/api/download")
async def start_download(background_tasks: BackgroundTasks, url: str = Form(...), format_type: str = Form("video"), quality: str = Form("720")):
    download_id = str(uuid.uuid4())[:8]
    ext = "mp3" if format_type == "audio" else "mp4"
    
    url_lower = url.lower()
    
    # 🚦 Traffic Police Logic: यूट्यूब को बाईपास में भेजो, बाकी को RapidAPI में
    if "youtube.com" in url_lower or "youtu.be" in url_lower:
        direct_url = fetch_youtube_api(url, format_type, quality)
    else:
        direct_url = fetch_rapidapi(url, format_type)
        
    if not direct_url:
        return JSONResponse(status_code=400, content={"error": "वीडियो लिंक नहीं मिल पाया। कृपया चेक करें कि वीडियो प्राइवेट तो नहीं है!"})
        
    background_tasks.add_task(download_file_background, download_id, direct_url, ext)
    return {"download_id": download_id, "status": "started"}

@app.get("/api/status/{download_id}")
async def get_status(download_id: str):
    if download_id not in active_downloads: raise HTTPException(status_code=404)
    return active_downloads[download_id]

@app.get("/api/download-file/{download_id}")
async def download_file(download_id: str):
    if download_id not in active_downloads: raise HTTPException(status_code=404)
    download = active_downloads[download_id]
    file_path = DOWNLOAD_DIR / download["filename"]
    if not file_path.exists(): raise HTTPException(status_code=404)
    ext = download.get("ext", "mp4")
    return FileResponse(path=file_path, filename=f"AnMusic_{download_id}.{ext}", media_type='audio/mpeg' if ext == 'mp3' else 'video/mp4')

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
    
