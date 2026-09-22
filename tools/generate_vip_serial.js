#!/usr/bin/env node
/**
 * FitnessRider 付費 VIP 序號簽發工具（ECDSA P-256）。
 *
 * 用途：
 *   1. 產生一組新的 P-256 金鑰對（第一次建置或金鑰輪替時用）：
 *        node tools/generate_vip_serial.js --gen-keypair
 *      私鑰只印在終端機，請自行存進「後端環境變數」（例如 Vercel 專案設定的
 *      VIP_SERIAL_PRIVATE_KEY_PEM），絕對不要存成檔案 commit 進 repo。
 *      公鑰不是機密，貼回 backend/src/lib/vipSerial.ts、
 *      android VersionLifecycleManager.kt、ios VersionLifecycleManager.swift
 *      三處的 VIP_SERIAL_PUBLIC_KEY_* 常數即可。
 *
 *   2. 簽發一組序號：
 *        VIP_SERIAL_PRIVATE_KEY_PEM="$(cat private.pem)" \
 *          node tools/generate_vip_serial.js --plan-days 365
 *      或用 --key-file 指向本機一個「不在 repo 內、也不會被 commit」的 PEM 檔：
 *        node tools/generate_vip_serial.js --plan-days 365 --key-file ../secrets/vip_private.pem
 *
 *      本工具只會把簽發出的序號印在終端機，不會寫入任何 repo 內的檔案，
 *      避免真實序號被誤 commit。序號本身需要搭配後端的兌換次數表
 *      （db.ts 的 vip_serial_redemptions）才能限制「同一組序號不能無限開通」，
 *      離線開通則只驗簽章、無法檢查兌換次數上限 —— 與既有推廣代碼的離線兌換
 *      機制有相同的已知限制（見 android/ios VersionLifecycleManager 的
 *      activateLicenseCode 離線 fallback 註解）。
 *
 * 序號格式與 payload 結構詳見 backend/src/lib/vipSerial.ts 的說明註解。
 */
const crypto = require('crypto');
const fs = require('fs');

function parseArgs(argv) {
  const args = { planDays: 365 };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--gen-keypair') args.genKeypair = true;
    else if (arg === '--plan-days') args.planDays = parseInt(argv[++i], 10);
    else if (arg === '--serial-id') args.serialId = argv[++i];
    else if (arg === '--key-file') args.keyFile = argv[++i];
    else if (arg === '--help' || arg === '-h') args.help = true;
  }
  return args;
}

function printHelp() {
  console.log(`用法：
  node tools/generate_vip_serial.js --gen-keypair
  node tools/generate_vip_serial.js --plan-days 365 [--serial-id AABBCCDD] [--key-file <path>]

私鑰來源優先順序：--key-file 指定的檔案 > VIP_SERIAL_PRIVATE_KEY_PEM 環境變數。
`);
}

function genKeypair() {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
  });
  const privPem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  const pubSpkiB64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

  console.log('=== 新的 P-256 金鑰對 ===');
  console.log('\n[公鑰 SPKI Base64] —— 可公開，貼到程式碼常數：');
  console.log(pubSpkiB64);
  console.log('\n[私鑰 PEM] —— 機密！只放進後端環境變數，絕對不要 commit：');
  console.log(privPem);
}

function loadPrivateKey(args) {
  let pem;
  if (args.keyFile) {
    pem = fs.readFileSync(args.keyFile, 'utf-8');
  } else if (process.env.VIP_SERIAL_PRIVATE_KEY_PEM) {
    pem = process.env.VIP_SERIAL_PRIVATE_KEY_PEM;
  } else {
    throw new Error(
      '找不到私鑰，請用 --key-file <path> 或設定環境變數 VIP_SERIAL_PRIVATE_KEY_PEM'
    );
  }
  return crypto.createPrivateKey(pem);
}

function buildPayload(serialIdHex, planDays) {
  if (!/^[0-9a-fA-F]{8}$/.test(serialIdHex)) {
    throw new Error('serialId 必須是 4 bytes（8 個 hex 字元）');
  }
  if (!Number.isInteger(planDays) || planDays <= 0 || planDays > 0xffff) {
    throw new Error('planDays 必須是 1..65535 的整數');
  }
  const buf = Buffer.alloc(7);
  buf.writeUInt8(1, 0);
  Buffer.from(serialIdHex, 'hex').copy(buf, 1);
  buf.writeUInt16BE(planDays, 5);
  return buf;
}

function generateSerial(args) {
  const privateKey = loadPrivateKey(args);
  const serialId = (args.serialId || crypto.randomBytes(4).toString('hex')).toUpperCase();
  const payload = buildPayload(serialId, args.planDays);
  const signature = crypto.sign('sha256', payload, privateKey);
  const serial = `FRVIP-${payload.toString('hex').toUpperCase()}-${signature.toString('hex').toUpperCase()}`;

  console.log(`序號 ID   : ${serialId}`);
  console.log(`開通天數  : ${args.planDays}`);
  console.log(`序號      : ${serial}`);
  console.log('\n請直接交付給購買者，不要把這組序號 commit 進 repo 或寫進任何版本控制的檔案。');
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || (!args.genKeypair && process.argv.length <= 2)) {
    printHelp();
    return;
  }
  if (args.genKeypair) {
    genKeypair();
    return;
  }
  generateSerial(args);
}

main();
