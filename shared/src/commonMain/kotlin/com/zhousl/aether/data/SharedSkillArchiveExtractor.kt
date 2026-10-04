package com.zhousl.aether.data

/**
 * Self-contained ZIP extractor executed by the runtime's Node 22.
 *
 * The Alpine runtime ships Node but not `unzip`, and installing packages at
 * import time makes first-use depend on network availability (silent apk
 * failures produced empty error messages and unbounded hangs). Running this
 * script keeps archive imports fully offline with hard entry/size limits.
 *
 * Supported: stored (0) and deflate (8) entries written by standard tools.
 * Rejected with explicit errors: encrypted entries, symlinks, ZIP64,
 * streaming data-descriptor entries, path traversal, oversized content.
 */
internal const val SharedSkillArchiveExtractorSource = """
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const MAX_ENTRIES = 4096;
const MAX_ENTRY_BYTES = 16 * 1024 * 1024;
const MAX_TOTAL_BYTES = 128 * 1024 * 1024;

function fail(message) {
  console.error(message);
  process.exit(1);
}

const args = process.argv.slice(2);
if (args.length !== 2) fail('Usage: skill-archive-extractor.cjs <archive> <dest>');
const archivePath = args[0];
const destDir = args[1];

let buf;
try {
  buf = fs.readFileSync(archivePath);
} catch (err) {
  fail('Unable to read archive: ' + err.message);
}
if (buf.length < 22) fail('Not a valid ZIP archive.');

let eocd = -1;
const scanFrom = Math.max(0, buf.length - 65557);
for (let i = buf.length - 22; i >= scanFrom; i--) {
  if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
}
if (eocd < 0) fail('Not a valid ZIP archive.');

const diskCount = buf.readUInt16LE(eocd + 10);
const cdSize = buf.readUInt32LE(eocd + 12);
const cdOffset = buf.readUInt32LE(eocd + 16);
if (diskCount === 0xffff || cdSize === 0xffffffff || cdOffset === 0xffffffff) {
  fail('ZIP64 archives are not supported.');
}

function safeJoin(base, name) {
  const normalized = String(name).replace(/\\/g, '/');
  if (normalized.indexOf('\u0000') >= 0) fail('Invalid archive entry path: ' + name);
  if (normalized.charAt(0) === '/') fail('Archive entry escapes the destination: ' + name);
  const segments = normalized.split('/').filter(function (s) { return s !== '' && s !== '.'; });
  if (segments.indexOf('..') >= 0) fail('Archive entry escapes the destination: ' + name);
  return path.join.apply(path, [base].concat(segments));
}

let totalBytes = 0;
let entryCount = 0;
let pos = cdOffset;
while (pos + 46 <= buf.length && buf.readUInt32LE(pos) === 0x02014b50) {
  const flags = buf.readUInt16LE(pos + 8);
  const method = buf.readUInt16LE(pos + 10);
  const csize = buf.readUInt32LE(pos + 20);
  const usize = buf.readUInt32LE(pos + 24);
  const nameLen = buf.readUInt16LE(pos + 28);
  const extraLen = buf.readUInt16LE(pos + 30);
  const commentLen = buf.readUInt16LE(pos + 32);
  const localOffset = buf.readUInt32LE(pos + 42);
  const name = buf.toString('utf8', pos + 46, pos + 46 + nameLen);
  pos += 46 + nameLen + extraLen + commentLen;

  entryCount++;
  if (entryCount > MAX_ENTRIES) fail('Archive contains too many entries.');
  if (name.charAt(name.length - 1) === '/') continue;
  if (flags & 1) fail('Encrypted archives are not supported.');
  if (method === 0x36) fail('Archive contains unsupported symbolic links.');
  if (method !== 0 && method !== 8) fail('Unsupported compression method for: ' + name);
  if (csize === 0 && usize === 0 && (flags & 8)) {
    fail('Streaming ZIP entries are not supported. Re-compress the archive.');
  }

  const target = safeJoin(destDir, name);
  fs.mkdirSync(path.dirname(target), { recursive: true });

  if (localOffset + 30 > buf.length || buf.readUInt32LE(localOffset) !== 0x04034b50) {
    fail('Corrupt archive entry: ' + name);
  }
  const localNameLen = buf.readUInt16LE(localOffset + 26);
  const localExtraLen = buf.readUInt16LE(localOffset + 28);
  const dataStart = localOffset + 30 + localNameLen + localExtraLen;
  if (dataStart + csize > buf.length) fail('Corrupt archive entry: ' + name);
  const data = buf.subarray(dataStart, dataStart + csize);

  let content;
  if (method === 8) {
    try {
      content = zlib.inflateRawSync(data);
    } catch (err) {
      fail('Failed to decompress: ' + name);
    }
    if (content.length !== usize) fail('Size mismatch for: ' + name);
  } else {
    content = Buffer.from(data);
  }
  if (content.length > MAX_ENTRY_BYTES) fail('Archive entry is too large: ' + name);
  totalBytes += content.length;
  if (totalBytes > MAX_TOTAL_BYTES) fail('Archive is too large.');
  fs.writeFileSync(target, content);
}
process.exit(0);
"""
