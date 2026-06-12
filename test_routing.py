import asyncio, json

class FakeWS:
    def __init__(self):
        self.msgs = []
        self.open = True
    async def send(self, msg):
        self.msgs.append(msg)
        print("FakeWS send:", msg)
    async def close(self, *args):
        self.open = False

async def test():
    from server import GroceryAlertServer
    s = GroceryAlertServer()
    dad = FakeWS()
    mom = FakeWS()
    s.clients["DAD"] = dad

    raw = json.dumps({"role":"MOM","item":"test","target":"DAD"})
    data = json.loads(raw)
    msg_role = data.get("role")
    has_item = bool(data.get("item", "").strip())
    role = None

    if msg_role in ("MOM", "DAD", "SON"):
        old = s.clients.get(msg_role)
        if old is not None and old.open:
            await old.close(1000, "Replaced")
        s.clients[msg_role] = mom
        role = msg_role
        print("Registered mom, clients:", list(s.clients.keys()))

    if role in ("MOM", "DAD", "SON"):
        action = data.get("action", "send")
        item = data.get("item", "").strip()
        if action != "bought" and item:
            other_roles = [r for r in ("MOM","DAD","SON") if r != role]
            target = data.get("target", other_roles[0]).upper()
            if target in other_roles:
                targets = [target]
            for t in targets:
                ws = s.clients.get(t)
                print(f"Lookup {t}: ws={ws is not None}, open={ws.open if ws else 'N/A'}")
                if ws is not None and ws.open:
                    await ws.send(json.dumps({"from":role,"item":item}))
                    print("Sent to dad!")
                else:
                    print("Dad offline!")
                    await mom.send(json.dumps({"status":"error","message":f"{t} offline"}))

    print("Dad msgs:", dad.msgs)
    print("Mom msgs:", mom.msgs)

asyncio.run(test())
