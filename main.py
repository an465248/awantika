import os
import uuid
import requests
from pathlib import Path
from fastapi import FastAPI, Request, Form, HTTPException, BackgroundTasks
from fastapi.responses import HTMLResponse, FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

app = FastAPI(title="AnMusic Downloader", version="3.0.0")

BASE_DIR = Path(__file__).resolve().parent
DOWNLOAD_DIR = BASE_DIR / "downloads"
DOWNLOAD_DIR.mkdir(exist_ok=True)

app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")
templates = Jinja2Templates(directory=BASE_DIR / "templates")

active_downloads = {}

def get_cobalt_download_url(video_url: str, format_type: str = "video"):
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
    # अगर ऑडियो चाहिए तो isAudioOnly को True कर देंगे
    payload = {
        "url": video_url,
        "isAudioOnly": True if format_type == "audio" else False
    }
    try:
        # यह API बिना किसी कुकीज़ के वीडियो का सीधा लिंक निकाल कर देगी
        response = requests.post("https://api.cobalt.tools/api/json", json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()
        if data.get("status") in ["redirect", "stream", "picker"]:
            return data.get("url")
        else:
            return None
    except Exception as e:
        print(f"API Error: {e}")
        return None

def download_file_background(download_id: str, direct_url: str, ext: str):
    try:
        active_downloads[download_id] = {"status": "downloading", "progress": 0, "filename": None, "title": f"AnMusic_{download_id}", "ext": ext}
        
        filename = f"{download_id}.{ext}"
        filepath = DOWNLOAD_DIR / filename
        
        response = requests.get(direct_url, stream=True)
        response.raise_for_status()
        
        total_size = int(response.headers.get('content-length', 0))
        block_size = 1024 * 1024 # 1MB chunks
        
        downloaded = 0
        with open(filepath, 'wb') as f:
            for data in response.iter_content(block_size):
                f.write(data)
                downloaded += len(data)
                if total_size > 0:
                    progress = int((downloaded / total_size) * 100)
                    active_downloads[download_id]["progress"] = progress
                    
        active_downloads[download_id].update({"status": "completed", "progress": 100, "filename": filename})
    except Exception as e:
        active_downloads[download_id] = {"status": "failed", "error": str(e), "progress": 0}

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    with open(BASE_DIR / "templates" / "index.html", "r", encoding="utf-8") as f:
        return HTMLResponse(content=f.read())

@app.post("/api/download")
async def start_download(background_tasks: BackgroundTasks, url: str = Form(...), format_type: str = Form("video"), quality: str = Form("best")):
    download_id = str(uuid.uuid4())[:8]
    ext = "mp3" if format_type == "audio" else "mp4"
    
    # 1. API से सीधा डाउनलोड लिंक मँगवाएँ
    direct_url = get_cobalt_download_url(url, format_type)
    
    if not direct_url:
        return JSONResponse(status_code=400, content={"error": "वीडियो प्राइवेट है या लिंक गलत है। कोई दूसरा लिंक आज़माएँ!"})
        
    # 2. बैकग्राउंड में सर्वर पर फाइल डाउनलोड करें
    background_tasks.add_task(download_file_background, download_id, direct_url, ext)
    return {"download_id": download_id, "status": "started"}

@app.get("/api/status/{download_id}")
async def get_status(download_id: str):
    if download_id not in active_downloads:
        raise HTTPException(status_code=404, detail="Not found")
    return active_downloads[download_id]

@app.get("/api/download-file/{download_id}")
async def download_file(download_id: str):
    if download_id not in active_downloads:
        raise HTTPException(status_code=404)
    download = active_downloads[download_id]
    file_path = DOWNLOAD_DIR / download["filename"]
    if not file_path.exists():
        raise HTTPException(status_code=404)
    
    ext = download.get("ext", "mp4")
    return FileResponse(path=file_path, filename=f"{download['title']}.{ext}", media_type='audio/mpeg' if ext == 'mp3' else 'video/mp4')

@app.get("/api/info")
async def get_video_info(url: str):
    return {"title": "AnMusic Download", "thumbnail": "", "duration": 0}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", 8000)))
    
