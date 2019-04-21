import { spawnSync, spawn } from 'child_process';
import express from 'express';
import status from 'http-status';
import fs from 'fs';

const { JOB_FILENAME } = process.env;

const router = express.Router();

router.post('/start', (req, res) => {
  console.log('Starting analyzer...');

  spawnSync('rm', ['-rf', '..\\analyzer\\compiled']);
  spawnSync('mkdir', ['..\\analyzer\\compiled']);
  // Disabling lint warning here because the non-literal comes from config
  // eslint-disable-next-line security/detect-non-literal-fs-filename
  fs.promises
    .writeFile(
      `..\\analyzer\\compiled\\${JOB_FILENAME}`,
      JSON.stringify(req.body)
    )
    .then(() =>
      spawnSync('javac', [
        '-cp',
        '".;..\\analyzer\\src\\json-20180813.jar;"',
        '-d',
        '..\\analyzer\\compiled',
        '..\\analyzer\\src\\*.java',
      ])
    )
    .then(() =>
      spawnSync(
        'java',
        ['-cp', '".;..\\src\\json-20180813.jar;"', 'Main', JOB_FILENAME],
        {
          cwd: '..\\analyzer\\compiled',
        }
      )
    )
    .then(() => res.status(status.OK).send('started'))
    .then(() => console.log(`Finished analyzer wtih ${JOB_FILENAME}`))
    .catch(err => {
      console.log(err);
      res.status(status.INTERNAL_SERVER_ERROR).send('failed to start');
    });
});

export default router;
