import express from 'express';
import status from 'http-status';

const router = express.Router();

router.post('/start', (req, res) => {
  console.log('TODO: Actually start');
  res.status(status.OK).send('started');
});

export default router;
