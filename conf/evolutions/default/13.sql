# --- !Ups
CREATE TABLE label_presampled
(
  label_id INTEGER NOT NULL,
  zoom_level INTEGER NOT NULL,
  PRIMARY KEY (label_id, zoom_level),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

# --- !Downs
DROP TABLE label_presampled;