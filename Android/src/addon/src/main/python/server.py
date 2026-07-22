from fastapi import FastAPI
from pydantic import BaseModel
import uvicorn
from com.google.ai.edge.gallery.addon import KotlinBridge

app = FastAPI()
bridge = None

class ChatRequest(BaseModel):
    message: str

@app.on_event("startup")
async def startup_event():
    # Bridge is set from Kotlin side during Python init
    pass

@app.post("/chat")
async def chat(request: ChatRequest):
    # This call needs to be serialized on Kotlin side to manage NPU
    response = bridge.runChat(request.message)
    return {"response": response}

@app.get("/status")
async def status():
    return {"status": "running"}

def start_server(bridge_instance):
    global bridge
    bridge = bridge_instance
    uvicorn.run(app, host="0.0.0.0", port=8000)
