import os
import uuid
import requests
import json
from pathlib import Path
from typing import Any, Dict, Optional
from fastapi import FastAPI, Request, Form, HTTPException, BackgroundTasks
from fastapi.responses import HTMLResponse, FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

app = FastAPI(title="AnMusic Downloader", version="6.0.0")

BASE_DIR = Path(__file__).resolve().parent
DOWNLOAD_DIR = BASE_DIR / "downloads"
DOWNLOAD_DIR.mkdir(exist_ok=True)

app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")
templates = Jinja2Templates(directory=BASE_DIR / "templates")

active_downloads = {}

RAPIDAPI_KEY = "ed12f7558bmsh321cb0f7580ccb0p1566d4jsne2c937cd594e"
RAPIDAPI_HOST = "social-download-all-in-one.p.rapidapi.com"

def extract_direct_url(data: Any, format_type: str = "video") -> Optional[str]:
    if isinstance(data, dict):
        if "medias" in data and isinstance(data["medias"], list) and len(data["medias"]) > 0:
            for item in data["medias"]:
                if format_type == "audio" and item.get("type") == "audio":
                    return item.get("url")
                if format_type == "video" and item.get("type") == "video":
                    return item.get("url")
            return data["medias"][0].get("url")
        for key in ["url", "download_url", "link", "video", "audio"]:
            if key in data and isinstance(data[key], str) and data[key].startswith("http"):
                return data[key]
        for v in data.values():
            res = extract_direct_url(v, format_type)
            if res: return res
    elif isinstance(data, list):
        for item in data:
            res = extract_direct_url(item, format_type)
            if res: return res
    return None

def fetch_from_rapidapi(target_url: str, format_type: str = "video"):
    api_url = f"https://{RAPIDAPI_HOST}/v1/social/autolink"
    headers = {
        "x-rapidapi-key": RAPIDAPI_KEY,
        "x-rapidapi-host": RAPIDAPI_HOST,
        "Content-Type": "application/json"
    }
    payload = {"url": target_url}
    try:
        response = requests.post(api_url, json=payload, headers=headers, timeout=25)
        data = response.json()
        url = extract_direct_url(data, format_type)
        return url, data
    except Exception as e:
        return None, str(e)

def download_file_background(download_id: str, direct_url: str, ext: str):
    try:
        active_downloads[download_id] = {"status": "downloading", "progress": 0, "filename": None, "title": f"Media_{download_id}", "ext": ext}
        filename = f"{download_id}.{ext}"
        filepath = DOWNLOAD_DIR / filename
        headers = {"User-Agent": "Mozilla/5.0"}
        response = requests.get(direct_url, headers=headers, stream=True, timeout=60)
        response.raise_for_status()
        total_size = int(response.headers.get('content-length', 0))
        downloaded = 0
        with open(filepath, 'wb') as f:
            for data in response.iter_content(1024 * 1024):
                f.write(data)
                downloaded += len(data)
                if total_size > 0:
                    active_downloads[download_id]["progress"] = int((downloaded / total_size) * 100)
        active_downloads[download_id].update({"status": "completed", "progress": 100, "filename": filename})
    except Exception as e:
        active_downloads[download_id] = {"status": "failed", "error": str(e), "progress": 0}

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    with open(BASE_DIR / "templates" / "index.html", "r", encoding="utf-8") as f:
        return HTMLResponse(content=f.read())

@app.post("/api/download")
async def start_download(background_tasks: BackgroundTasks, url: str = Form(...), format_type: str = Form("video")):
    download_id = str(uuid.uuid4())[:8]
    ext = "mp3" if format_type == "audio" else "mp4"
    
    direct_url, debug_data = fetch_from_rapidapi(url, format_type)
    if not direct_url:
        # अब एरर फोन पर दिखेगा!
        error_msg = f"API Error: {json.dumps(debug_data)[:250]}"
        return JSONResponse(status_code=400, content={"error": error_msg})
        
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

@app.get("/api/info")
async def get_video_info(url: str):
    return {
        "title": "Ready to Download",
        "thumbnail": "https://img.freepik.com/free-vector/video-player-interface-design_1017-33336.jpg",
        "duration": 0,
        "formats": [
            {"format_id": "video", "ext": "mp4", "format_note": "High Quality", "resolution": "HD"},
            {"format_id": "audio", "ext": "mp3", "format_note": "High Quality", "resolution": "Audio"}
        ]
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
    
