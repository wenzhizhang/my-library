#! /bin/bash

source .venv/bin/activate

nohup python main.py > server.log 2>&1 &
echo "Backend server started. Logs are being written to server.log"

cd frontend
nohup npm start > app.log 2>&1 &
echo "Frontend app started. Logs are being written to app.log"

