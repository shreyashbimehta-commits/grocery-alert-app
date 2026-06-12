import asyncio
import json
import websockets

async def dad_client():
    async with websockets.connect("ws://localhost:8766") as ws:
        await ws.send(json.dumps({"role": "DAD"}))
        print("Dad reg:", await asyncio.wait_for(ws.recv(), 5))
        print("Dad waiting for alert...")
        msg = await asyncio.wait_for(ws.recv(), 10)
        print("Dad got alert:", msg)

async def mom_client():
    await asyncio.sleep(1)
    async with websockets.connect("ws://localhost:8766") as ws:
        await ws.send(json.dumps({"role": "MOM", "item": "milk", "target": "DAD"}))
        try:
            while True:
                msg = await asyncio.wait_for(ws.recv(), 3)
                print("Mom got:", msg)
        except asyncio.TimeoutError:
            print("Mom done")

async def main():
    server = await websockets.serve(
        lambda ws: handle(ws),
        "0.0.0.0", 8766
    )
    print("Test server on :8766")
    await asyncio.sleep(0.5)
    await asyncio.gather(dad_client(), mom_client())
    server.close()
    await server.wait_closed()

clients = {}
async def handle(websocket):
    remote = websocket.remote_address
    role = None
    async for raw in websocket:
        data = json.loads(raw)
        msg_role = data.get("role")
        has_item = bool(data.get("item", "").strip())
        if msg_role in ("MOM", "DAD", "SON"):
            old = clients.get(msg_role)
            if old and old.open:
                await old.close(1000, "replaced")
            clients[msg_role] = websocket
            role = msg_role
            print(f"Registered {role}, clients: {list(clients.keys())}")
            await websocket.send(json.dumps({"status": "registered", "role": role}))
            if not has_item:
                continue
        if role in ("MOM", "DAD", "SON"):
            action = data.get("action", "send")
            item = data.get("item", "").strip()
            if action != "bought" and item:
                other_roles = [r for r in ("MOM","DAD","SON") if r != role]
                target = data.get("target", other_roles[0]).upper()
                if target in other_roles:
                    targets = [target]
                delivered_any = False
                for t in targets:
                    ws = clients.get(t)
                    print(f"Routing to {t}: ws={ws is not None}, open={ws.open if ws else 'N/A'}")
                    if ws and ws.open:
                        await ws.send(json.dumps({"from": role, "item": item}))
                        delivered_any = True
                        await websocket.send(json.dumps({"status": "delivered", "to": t}))
                    else:
                        await websocket.send(json.dumps({"status": "error", "message": f"{t} offline"}))
                if not delivered_any:
                    print(f"No one online for {item}")

asyncio.run(main())
