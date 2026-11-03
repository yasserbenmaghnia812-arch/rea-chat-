const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");
const Busboy = require("busboy");

const HOST = "127.0.0.1";
const PORT = Number(process.env.PORT || 8765);
const MAX_BYTES = 150 * 1024 * 1024;

const server = http.createServer((req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  if (req.method === "OPTIONS") {
    res.writeHead(204, { "Access-Control-Allow-Methods": "GET,POST,OPTIONS", "Access-Control-Allow-Headers": "Content-Type" });
    return res.end();
  }
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ ok: true, engine: "REA", host: HOST, port: PORT }));
  }
  if (req.method !== "POST" || req.url !== "/analyze") {
    res.writeHead(404); return res.end("Not found");
  }

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "rea-upload-"));
  let apkPath = null;
  let size = 0;
  let failed = false;
  const bb = Busboy({ headers: req.headers, limits: { files: 1, fileSize: MAX_BYTES } });
  bb.on("file", (_name, file, info) => {
    const filename = (info.filename || "upload.apk").replace(/[^a-zA-Z0-9._-]/g, "_");
    if (!filename.toLowerCase().endsWith(".apk")) { failed = true; file.resume(); return; }
    apkPath = path.join(tempDir, filename);
    const out = fs.createWriteStream(apkPath);
    file.on("data", chunk => { size += chunk.length; });
    file.on("limit", () => { failed = true; });
    file.pipe(out);
  });
  bb.on("error", () => { failed = true; });
  bb.on("close", () => {
    if (failed || !apkPath || size === 0) {
      fs.rmSync(tempDir, { recursive: true, force: true });
      res.writeHead(400, { "content-type": "application/json" });
      return res.end(JSON.stringify({ error: "Upload invalid, empty, or larger than 150 MB." }));
    }
    const child = spawn("rea", ["inspect-android-package", apkPath, "--json"], { env: process.env });
    let stdout = "", stderr = "";
    child.stdout.on("data", d => { stdout += d.toString(); });
    child.stderr.on("data", d => { stderr += d.toString(); });
    child.on("close", code => {
      fs.rmSync(tempDir, { recursive: true, force: true });
      res.writeHead(code === 0 ? 200 : 500, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify(code === 0 ? { ok: true, result: stdout } : { ok: false, error: stderr || stdout || "REA failed" }));
    });
  });
  req.pipe(bb);
});

server.listen(PORT, HOST, () => console.log(`REA local API listening at http://${HOST}:${PORT}`));
