from fastapi import FastAPI

app = FastAPI(title="Dan-burn-go AI Service")


@app.get("/health")
def health():
    return {"status": "ok"}
