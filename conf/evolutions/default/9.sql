
# --- !Ups
CREATE TABLE clustering_session_label_type_threshold (
  clustering_session_label_type_threshold_id SERIAL NOT NULL,
  clustering_session_id INT NOT NULL,
  label_type_id INT NOT NULL,
  clustering_threshold DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (clustering_session_label_type_threshold_id),
  FOREIGN KEY (clustering_session_id) REFERENCES clustering_session(clustering_session_id),
  FOREIGN KEY (label_type_id) REFERENCES label_type(label_type_id)
);


# --- !Downs
DROP TABLE clustering_session_label_type_threshold;
