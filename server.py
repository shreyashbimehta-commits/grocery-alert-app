#!/usr/bin/env python3
import asyncio
import json
import logging
import os
from datetime import datetime, timezone

import websockets
from websockets.server import WebSocketServerProtocol
from websockets.http import Headers

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("GroceryServer")

HOST = "0.0.0.0"
PORT = int(os.environ.get("PORT", 8765))


class GroceryAlertServer:
    def __init__(self):
        self.clients: dict[str, WebSocketServerProtocol] = {}

    async def handle_client(self, websocket: WebSocketServerProtocol):
        remote = websocket.remote_address
        logger.info(f"New connection from {remote}")
        role = None
        try:
            async for raw in websocket:
                try:
                    data = json.loads(raw)
                except json.JSONDecodeError:
                    logger.warning(f"Invalid JSON from {remote}: {raw}")
                    await websocket.send(
                        json.dumps({"status": "error", "message": "invalid JSON"})
                    )
                    continue

                msg_role = data.get("role")
                has_item = bool(data.get("item", "").strip())

                if msg_role in ("MOM", "DAD", "SON"):
                    old = self.clients.get(msg_role)
                    if old is not None and old.open:
                        logger.info(f"Replacing existing {msg_role} connection")
                        await old.close(1000, "Replaced by new connection")
                    self.clients[msg_role] = websocket
                    role = msg_role
                    logger.info(f"Registered {role} ({remote})")
                    await websocket.send(
                        json.dumps({"status": "registered", "role": role})
                    )
                    if not has_item:
                        continue

                if role in ("MOM", "DAD", "SON"):
                    action = data.get("action", "send")
                    item = data.get("item", "").strip()

                    if action == "bought":
                        target = data.get("target", "").upper()
                        if target not in ("MOM", "DAD", "SON") or target == role:
                            await websocket.send(
                                json.dumps({"status": "error", "message": "invalid target"})
                            )
                            continue
                        ws = self.clients.get(target)
                        if ws is not None and ws.open:
                            payload = json.dumps(
                                {
                                    "from": role,
                                    "action": "bought",
                                    "item": item,
                                    "timestamp": datetime.now(timezone.utc).isoformat(),
                                }
                            )
                            await ws.send(payload)
                            logger.info(f"Bought confirmation '{item}' from {role} to {target}")
                            await websocket.send(
                                json.dumps(
                                    {
                                        "status": "delivered",
                                        "action": "bought",
                                        "to": target,
                                    }
                                )
                            )
                        else:
                            await websocket.send(
                                json.dumps(
                                    {
                                        "status": "error",
                                        "message": f"{target} is offline",
                                    }
                                )
                            )
                        continue

                    if not item:
                        await websocket.send(
                            json.dumps(
                                {"status": "error", "message": "item is required"}
                            )
                        )
                        continue

                    other_roles = [r for r in ("MOM", "DAD", "SON") if r != role]
                    target = data.get("target", other_roles[0]).upper()

                    if target == "BOTH":
                        targets = other_roles
                    elif target in other_roles:
                        targets = [target]
                    elif target == role:
                        await websocket.send(
                            json.dumps(
                                {
                                    "status": "error",
                                    "message": f"Cannot send to yourself ({role})",
                                }
                            )
                        )
                        continue
                    else:
                        await websocket.send(
                            json.dumps(
                                {
                                    "status": "error",
                                    "message": f"Invalid target '{target}'",
                                }
                            )
                        )
                        continue

                    delivered_any = False
                    for t in targets:
                        ws = self.clients.get(t)
                        if ws is not None and ws.open:
                            payload = json.dumps(
                                {
                                    "from": role,
                                    "item": item,
                                    "target": t,
                                    "timestamp": datetime.now(timezone.utc).isoformat(),
                                }
                            )
                            await ws.send(payload)
                            logger.info(f"Routed item '{item}' from {role} to {t}")
                            delivered_any = True
                            await websocket.send(
                                json.dumps(
                                    {
                                        "status": "delivered",
                                        "to": t,
                                        "item": item,
                                    }
                                )
                            )
                        else:
                            await websocket.send(
                                json.dumps(
                                    {
                                        "status": "error",
                                        "to": t,
                                        "message": f"{t} is offline",
                                    }
                                )
                            )

                    if not delivered_any:
                        logger.warning(
                            f"No one online for '{item}' (from={role}, target={target})"
                        )
                else:
                    logger.warning(f"Unexpected message from {role or 'unknown'}: {raw}")

        except websockets.exceptions.ConnectionClosed as e:
            logger.info(f"{role or remote} disconnected: {e.code} {e.reason}")
        finally:
            if role and self.clients.get(role) is websocket:
                del self.clients[role]
                logger.info(f"Removed {role} from registry")

    async def broadcast_status(self):
        while True:
            await asyncio.sleep(30)
            online = [r for r, ws in self.clients.items() if ws.open]
            logger.info(f"Connected clients: {online if online else 'none'}")

    async def handle_health(self, path, request_headers):
        if path == "/health":
            return request_headers.raw_path, 200, Headers({"Content-Type": "text/plain"}), b"OK"
        return None

    async def start(self):
        async with websockets.serve(
            self.handle_client, HOST, PORT,
            ping_interval=20, ping_timeout=10,
            process_request=self.handle_health,
        ):
            logger.info(f"Server listening on {HOST}:{PORT}")
            logger.info(f"Health check at http://0.0.0.0:{PORT}/health")
            await asyncio.Future()


def main():
    server = GroceryAlertServer()
    try:
        asyncio.run(server.start())
    except KeyboardInterrupt:
        logger.info("Server shutting down")


if __name__ == "__main__":
    main()
