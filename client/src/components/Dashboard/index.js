import React, { Component } from 'react';
import { Button } from 'react-bootstrap';
import { compose } from 'recompose';

import Banner from '../Banner';
import GameSelector from '../GameSelector';
import { withAuthorization, withEmailVerification } from '../Session';
import { withFirebase } from '../Firebase';

import 'bootstrap/dist/css/bootstrap.css';
import './Dashboard.css';

class HostButton extends Component {
  render() {
    return (
      <Button variant="secondary">host</Button>
    );
  }
}

class JoinButton extends Component {
  render() {
    return (
      <Button variant="secondary">join</Button>
    );
  }
}

class RandomButton extends Component {
  render() {
    return (
      <Button variant="secondary">random</Button>
    );
  }
}

class Dashboard extends Component {
  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      user: null,
      ...props.location.state,
    }
  }

  componentDidMount() {
    if (this.state.user) {
      return;
    }

    this.setState({ loading: true });

    this.unsubscribe = this.props.firebase
      .user(this.props.match.params.id)
      .onSnapshot(snapshot => {
        this.setState({
          user: snapshot.data(),
          loading: false,
        });
      });
  }

  componentWillUnmount() {
    this.unsubscribe && this.unsubscribe();
  }

  render() {
    // Hard coded for now, these games would be taken from the database
    const games = ['oo0j05CCUJNqQxSFSNEI'];
    const { user, loading } = this.state;

    return (
      <div className="banner-wrapper">
        <Banner />
        {loading && <div>Loading...</div>}
        {user && (
          <div>
            <div className="greeting">
              <p>Hi, {user.username}! Here are your games:</p>
            </div>
            <div className="dashboard-buttons">
              <HostButton />{' '}
              <JoinButton />{' '}
              <RandomButton />
            </div>
            <div className="games-list">
              <GameSelector id={games[0]}/>
            </div>
          </div>
        )}
      </div>
    );
  }
}

const condition = authUser => !!authUser;

export default compose(
  withEmailVerification,
  withAuthorization(condition),
  withFirebase,
)(Dashboard);
