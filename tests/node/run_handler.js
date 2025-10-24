#!/usr/bin/env node
const path = require('path');

const [, , modulePath, exportName = 'handler'] = process.argv;

if (!modulePath) {
  console.error('Module path argument is required');
  process.exit(1);
}

const resolvedModulePath = path.resolve(modulePath);

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  input += chunk;
});

const parseEvent = () => {
  if (!input) {
    return undefined;
  }

  try {
    return JSON.parse(input);
  } catch (err) {
    console.error('Failed to parse event payload:', err);
    process.exit(1);
  }
};

process.stdin.on('end', async () => {
  const event = parseEvent();
  try {
    // eslint-disable-next-line import/no-dynamic-require, global-require
    const handlerModule = require(resolvedModulePath);
    const handler = handlerModule[exportName];

    if (typeof handler !== 'function') {
      throw new Error(`Export "${exportName}" is not a function on module ${resolvedModulePath}`);
    }

    const result = await handler(event, {});
    if (result !== undefined) {
      process.stdout.write(`${JSON.stringify(result)}\n`);
    }
  } catch (err) {
    console.error(err);
    process.exit(1);
  }
});

process.stdin.resume();
