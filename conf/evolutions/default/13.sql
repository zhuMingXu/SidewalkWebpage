# --- !Ups
CREATE TABLE label_presampled
(
  label_id INTEGER NOT NULL,
  zoom_level INTEGER NOT NULL,
  PRIMARY KEY (label_id, zoom_level),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z0
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z1
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z2
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z3
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z4
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z5
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z6
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

CREATE TABLE label_presampled_z7
(
  label_id INTEGER NOT NULL,
  PRIMARY KEY (label_id),
  FOREIGN KEY (label_id) REFERENCES label(label_id)
);

# --- !Downs
DROP TABLE label_presampled;
DROP TABLE label_presampled_z0;
DROP TABLE label_presampled_z1;
DROP TABLE label_presampled_z2;
DROP TABLE label_presampled_z3;
DROP TABLE label_presampled_z4;
DROP TABLE label_presampled_z5;
DROP TABLE label_presampled_z6;
DROP TABLE label_presampled_z7;