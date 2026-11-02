"""Minimal mcdebug JSON-RPC client over the unix socket (one connection, newline-delimited).
Used by the M-parity probes so thousands of calls do not each start a JVM.
Server plane: <gameDir>/mcdebug/socket ; client plane: <gameDir>/mcdebug/clientSocket.
"""
import json, os, socket

GAME_DIRS = {
    "neo": "/tmp/mekanism-neoforge/runs/client",
}
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

def game_dir(side, module="core"):
    if side == "neo":
        return GAME_DIRS["neo"]
    if side == "fabsrv":  # disposable dedicated Fabric server with the four release jars (scripts/parity/fab_server.sh)
        return os.environ.get("PARITY_FAB_SERVER_DIR", "/tmp/parity-fab-server")
    return os.path.join(ROOT, "fabric", "run" if module == "core" else f"run-{module}")

class RpcError(Exception):
    def __init__(self, code, message, data=None):
        super().__init__(f"[{code}] {message}")
        self.code, self.message, self.data = code, message, data

class Rpc:
    def __init__(self, side, module="core", plane="server"):
        sock = "socket" if plane == "server" else "clientSocket"
        self.path = os.path.join(game_dir(side, module), "mcdebug", sock)
        self.s = socket.socket(socket.AF_UNIX)
        self.s.settimeout(120)
        self.s.connect(self.path)
        self.buf = b""
        self.n = 0

    def _line(self):
        while b"\n" not in self.buf:
            chunk = self.s.recv(1 << 20)
            if not chunk:
                raise ConnectionError("mdb socket closed")
            self.buf += chunk
        line, self.buf = self.buf.split(b"\n", 1)
        return json.loads(line)

    def call(self, method, params=None):
        self.n += 1
        rid = self.n
        self.s.sendall((json.dumps({"jsonrpc": "2.0", "id": rid, "method": method, "params": params or {}}) + "\n").encode())
        while True:
            msg = self._line()
            if msg.get("id") != rid:  # server notifications (server.ready) carry no id
                continue
            if "error" in msg:
                e = msg["error"]
                raise RpcError(e.get("code"), e.get("message"), e.get("data"))
            return msg["result"]

    def command(self, cmd, tolerate=("No blocks were filled", "No items were found", "Nothing changed", "No chunks were marked", "already")):
        try:
            return self.call("server.runCommand", {"command": cmd})
        except RpcError as e:
            if any(t in e.message for t in tolerate):
                return {"success": False, "output": e.message}
            raise

    def close(self):
        self.s.close()
