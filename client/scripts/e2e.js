/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const fetch = require('node-fetch');
const createTestCafe = require('testcafe');
const chalk = require('chalk');

const {spawn} = require('child_process');
const net = require('net');
const kill = require('tree-kill');
const fs = require('fs');

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;

// argument to determine if we want to use headlessChrome instead of default Browserstack
const chromeheadlessMode = process.argv.indexOf('chromeheadless') > -1;

console.debug(
  'executing e2e script in [ci=' + ciMode + ', chromeheadlessMode=' + chromeheadlessMode + ']'
);

if (!ciMode) {
  // credentials for local testing, in CI we get credentials from jenkins
  process.env.BROWSERSTACK_USERNAME = 'optimize@camunda.com';
  process.env.BROWSERSTACK_ACCESS_KEY = 'QDQfPYkTYy8SQBYYt1zB';
}
process.env.BROWSERSTACK_USE_AUTOMATE = '1';
process.env.BROWSERSTACK_DISPLAY_RESOLUTION = '1920x1080';

const browsers = chromeheadlessMode
  ? ['chrome:headless']
  : ['browserstack:Edge', 'browserstack:Firefox', 'browserstack:Chrome'];

const backendProcess = spawn('yarn', ['run', 'start-backend', ciMode ? 'ci' : undefined]);
const frontendProcess = spawn('yarn', ['start']);

if (ciMode) {
  backendProcess.stderr.on('data', data => console.error(data.toString()));

  const logStream = fs.createWriteStream('./build/backendLogs.log', {flags: 'a'});
  backendProcess.stdout.pipe(logStream);
  backendProcess.stderr.pipe(logStream);
}

let dataInterval;
const connectionInterval = setInterval(async () => {
  const backendDone = await checkPort(8090);
  const frontendDone = await checkPort(3000);

  console.log(
    `waiting for servers to be started: backend = ${
      backendDone ? 'started' : 'not started'
    } , frontend = ${frontendDone ? 'started' : 'not started'}`
  );

  if (backendDone && frontendDone) {
    console.log(chalk.green.bold('Servers Started!'));
    clearInterval(connectionInterval);
    dataInterval = setInterval(waitForData, 1000);
  }
}, 5000);

function checkPort(number) {
  return new Promise(resolve => {
    const socket = new net.Socket();

    const destroy = () => {
      socket.destroy();
      resolve(false);
    };

    socket.setTimeout(1000);
    socket.once('error', destroy);
    socket.once('timeout', destroy);

    socket.connect(number, 'localhost', () => {
      socket.end();
      resolve(true);
    });
  });
}

async function waitForData() {
  const generatorResponse = await fetch('http://localhost:8100/api/dataGenerationComplete');
  const status = await generatorResponse.text();

  if (status === 'false') {
    console.log('Still generating data');
  } else {
    const resp = await fetch('http://localhost:8090/api/status');
    const status = await resp.json();

    const {
      connectionStatus: {engineConnections, connectedToElasticsearch},
      isImporting
    } = status;

    if (
      connectedToElasticsearch &&
      engineConnections['camunda-bpm'] &&
      !isImporting['camunda-bpm']
    ) {
      console.log(chalk.green.bold('Data Available! Starting tests.'));
      clearInterval(dataInterval);
      startTest();
    } else {
      console.log('Waiting for data import');
    }
  }
}

async function startTest() {
  const testCafe = await createTestCafe('localhost');
  let hasFailures = false;
  try {
    for (let i = 0; i < browsers.length; i++) {
      if (
        await testCafe
          .createRunner()
          .src('e2e/tests/*.js')
          .browsers(browsers[i])
          .run({
            skipJsErrors: true,
            disableScreenshots: true,
            concurrency: 3,
            assertionTimeout: 10000,
            pageLoadTimeout: 10000
          })
      ) {
        hasFailures = true;
      }
    }
  } finally {
    await testCafe.close();
    kill(frontendProcess.pid, () => {
      kill(backendProcess.pid, () => {
        process.exit(hasFailures ? 3 : 0);
      });
    });
  }
}
