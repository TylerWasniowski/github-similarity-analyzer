// @flow
import '../styles/global.css';
import React, { useState } from 'react';
import { hot } from 'react-hot-loader';

// Material-ui
import Button from '@material-ui/core/Button';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import TextField from '@material-ui/core/TextField';
import Typography from '@material-ui/core/Typography';
import { Divider } from '@material-ui/core';

function addHttps(url) {
  return url.startsWith('http://') || url.startsWith('https://')
    ? url
    : `https://${url}`;
}

function splitUrls(urlString: string) {
  const urlPieces = urlString.split(';').map(url => url.trim());

  const users = urlPieces
    .filter(url => /.*github.com\/[^/]*\/?$/.test(url))
    .map(addHttps);
  const repos = urlPieces
    .filter(url => /.*github.com\/.*\/.+$/.test(url))
    .map(addHttps);

  return {
    users,
    repos,
  };
}

const Home = () => {
  const [urls, setUrls] = useState({
    users: [],
    repos: [],
  });

  return (
    <React.Fragment>
      <Typography
        component="h1"
        className="page-title"
        variant="title"
        color="inherit"
        noWrap
      >
        GitHub Similarity Analyzer
      </Typography>
      <TextField
        id="urls-input"
        label="URLs"
        className="input"
        name="urls"
        margin="normal"
        variant="outlined"
        onChange={event => setUrls(splitUrls(event.target.value))}
      />
      <br />
      <br />
      <Button
        color="primary"
        variant="contained"
        onClick={() =>
          fetch('/start', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(urls),
          })
        }
      >
        Analyze
      </Button>
      <br />
      <br />
      <br />
      <div
        className="urls-list"
        hidden={!(urls.users.length || urls.repos.length)}
      >
        <List>
          {urls.users.map(user => (
            <ListItem
              button
              component="a"
              href={`https://www.github.com/${user}`}
            >
              <ListItemText primary={user} />
            </ListItem>
          ))}
        </List>
        {urls.users.length && urls.repos.length ? (
          <Divider />
        ) : (
          <React.Fragment />
        )}
        <List>
          {urls.repos.map(repo => (
            <ListItem
              button
              component="a"
              href={`https://www.github.com/${repo}`}
            >
              <ListItemText primary={repo} />
            </ListItem>
          ))}
        </List>
      </div>
    </React.Fragment>
  );
};

export default hot(module)(Home);
