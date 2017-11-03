
# --- !Ups
ALTER TABLE clustering_session
  ALTER COLUMN route_id DROP NOT NULL;

ALTER TABLE clustering_session
  ADD user_id TEXT;

ALTER TABLE clustering_session_cluster
  ADD label_type_id INT;

ALTER TABLE clustering_session_cluster
  ADD lat DOUBLE PRECISION;

ALTER TABLE clustering_session_cluster
  ADD lng DOUBLE PRECISION;

ALTER TABLE clustering_session_cluster
  ADD severity INT;

ALTER TABLE clustering_session_cluster
  ADD temporary BOOLEAN;



# --- !Downs
ALTER TABLE clustering_session
  ALTER COLUMN route_id SET NOT NULL;

ALTER TABLE clustering_session
  DROP user_id;

ALTER TABLE clustering_session_cluster
  DROP label_type_id;

ALTER TABLE clustering_session_cluster
  DROP lat;

ALTER TABLE clustering_session_cluster
  DROP lng;

ALTER TABLE clustering_session_cluster
  DROP severity;

ALTER TABLE clustering_session_cluster
  DROP temporary;
