import 'pretty-error/start';
import 'dotenv/config';
import express from 'express';
import { promisify } from 'util';
import expressSession from 'express-session';

import assets from 'github-similarity-analyzer-frontend';

import indexRouter from './routes/index';

const port = process.env.PORT || 2000;
process.on('unhandledRejection', err => {
  throw err;
});

(async () => {
  const app = express();

  // this sets the public directory to the frontend package's build directory
  app.use(express.static(assets));

  // app.use(logger('dev'));
  app.use(express.json());
  app.use(express.urlencoded({ extended: false }));
  // app.use(express.static(path.join(__dirname, 'public')));
  app.use(
    expressSession({ secret: 'max', saveUninitialized: false, resave: false })
  );

  // set API routes here
  app.use('/', indexRouter);

  // wait until the app starts
  await promisify(app.listen).bind(app)(port);
  console.log('app started');
})();
