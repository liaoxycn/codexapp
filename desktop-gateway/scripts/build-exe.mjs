import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import * as esbuild from "esbuild";

const root = fileURLToPath(new URL("../", import.meta.url));
const distDir = join(root, "dist");
const seaDir = join(root, "build-sea");
const bundlePath = join(distDir, "gateway.bundle.cjs");
const blob = join(seaDir, "gateway.blob");
const seaConfig = join(seaDir, "sea-config.json");
const exePath = join(distDir, "desktop-gateway.exe");
const nodeExe = process.execPath;
const tsc = process.platform === "win32"
  ? ["cmd", ["/c", "node_modules\\.bin\\tsc.cmd", "-p", "tsconfig.json"]]
  : [join(root, "node_modules", ".bin", "tsc"), ["-p", "tsconfig.json"]];
const postject = process.platform === "win32"
  ? ["cmd", ["/c", "node_modules\\.bin\\postject.cmd"]]
  : [join(root, "node_modules", ".bin", "postject"), []];

rmSync(seaDir, { recursive: true, force: true });
rmSync(exePath, { force: true });
mkdirSync(seaDir, { recursive: true });

execFileSync(tsc[0], tsc[1], {
  cwd: root,
  stdio: "inherit",
});

await esbuild.build({
  entryPoints: [join(root, "dist", "index.js")],
  bundle: true,
  platform: "node",
  target: "node24",
  format: "cjs",
  outfile: bundlePath,
  packages: "external",
  minify: true,
  legalComments: "none",
  sourcemap: false,
  treeShaking: true,
});

writeFileSync(
  seaConfig,
  JSON.stringify(
    {
      main: "dist/gateway.bundle.cjs",
      output: "build-sea/gateway.blob",
      assets: {},
      disableExperimentalSEAWarning: true,
    },
    null,
    2
  )
);

execFileSync(nodeExe, ["--experimental-sea-config", seaConfig], {
  cwd: root,
  stdio: "inherit",
});

writeFileSync(exePath, readFileSync(nodeExe));
execFileSync(postject[0], [...postject[1], exePath, "NODE_SEA_BLOB", blob, "--sentinel-fuse", "NODE_SEA_FUSE_fce680ab2cc467b6e072b8b5df1996b2"], {
  cwd: root,
  stdio: "inherit",
});

console.log(exePath);
