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

function splitSelected(selectedString: string) {
  const userRegex = /^(.*github.com\/)?([^/\s.]+)\/?$/m;
  const repoRegex = /^(.*github.com\/)?([^/\s.]+\/[^/\s.]+)$/m;

  const selectedPieces = selectedString.split(';').map(item => item.trim());

  const users = selectedPieces
    .filter(item => userRegex.test(item))
    .map(item => item.replace(userRegex, '$2'));
  const repos = selectedPieces
    .filter(item => repoRegex.test(item))
    .map(item => item.replace(repoRegex, '$2'));

  return {
    users,
    repos,
  };
}

const Home = () => {
  const [selected, setSelected] = useState({
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
        onChange={event => setSelected(splitSelected(event.target.value))}
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
            body: JSON.stringify(selected),
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
        hidden={!selected.users.length && !selected.repos.length}
      >
        <List>
          {selected.users.map(user => (
            <ListItem
              key={user}
              button
              component="a"
              href={`https://www.github.com/${user}`}
            >
              <ListItemText primary={user} />
            </ListItem>
          ))}
        </List>
        {selected.users.length && selected.repos.length ? (
          <Divider />
        ) : (
          <React.Fragment />
        )}
        <List>
          {selected.repos.map(repo => (
            <ListItem
              key={repo}
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
