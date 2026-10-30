import { access, cp } from "node:fs/promises";
import { spawn } from "node:child_process";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath } from "node:url";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const standalone=join(root,".next/standalone");
try { await access(join(standalone,"server.js")); }
catch { console.error("Build de produção não encontrado. Execute npm run build antes de npm start.");process.exit(1); }
await cp(join(root,"public"),join(standalone,"public"),{recursive:true,force:true});
await cp(join(root,".next/static"),join(standalone,".next/static"),{recursive:true,force:true});
const server=spawn(process.execPath,[join(standalone,"server.js")],{
  cwd:root,stdio:"inherit",env:{...process.env,HOSTNAME:process.env.FRONTEND_HOST??"127.0.0.1",PORT:process.env.PORT??"3000"},
});
for(const signal of ["SIGINT","SIGTERM"]) process.on(signal,()=>server.kill(signal));
server.on("error",error=>{console.error(error.message);process.exitCode=1;});
server.on("exit",(code,signal)=>{process.exit(signal?0:code??1);});
