
# --- !Ups
CREATE TABLE user_clustering_session (
  user_clustering_session_id SERIAL NOT NULL,
  is_registered BOOLEAN NOT NULL,
  user_id TEXT,
  ip_address TEXT,
  clustering_session_id INT NOT NULL,
  PRIMARY KEY (user_clustering_session_id),
  FOREIGN KEY (user_id) REFERENCES "user" (user_id),
  FOREIGN KEY (clustering_session_id) REFERENCES clustering_session(clustering_session_id)
);

CREATE TABLE issue_clustering_session (
  issue_clustering_session_id SERIAL NOT NULL,
  clustering_session_id INT NOT NULL,
  PRIMARY KEY (issue_clustering_session_id),
  FOREIGN KEY (clustering_session_id) REFERENCES clustering_session(clustering_session_id)
);


# --- !Downs
DROP TABLE user_clustering_session;

DROP TABLE issue_clustering_session;
