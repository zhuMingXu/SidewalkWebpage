
# --- !Ups
ALTER TABLE clustering_session
  ALTER COLUMN route_id DROP NOT NULL;

ALTER TABLE clustering_session
  ADD user_id TEXT,
  ADD turker_id TEXT;

ALTER TABLE clustering_session_cluster
  ADD label_type_id INT,
  ADD lat DOUBLE PRECISION,
  ADD lng DOUBLE PRECISION,
  ADD severity INT,
  ADD temporary BOOLEAN;

ALTER TABLE clustering_session_label
  ALTER COLUMN label_id DROP NOT NULL,
  ADD originating_cluster_id INT,
  ADD FOREIGN KEY (originating_cluster_id) REFERENCES clustering_session_cluster(clustering_session_cluster_id);



# --- !Downs
ALTER TABLE clustering_session
  ALTER COLUMN route_id SET NOT NULL;

ALTER TABLE clustering_session
  DROP user_id,
  DROP turker_id;

ALTER TABLE clustering_session_cluster
  DROP label_type_id,
  DROP lat,
  DROP lng,
  DROP severity,
  DROP temporary;

ALTER TABLE clustering_session_label
  ALTER COLUMN label_id SET NOT NULL,
  DROP CONSTRAINT IF EXISTS clustering_session_label_originating_cluster_id_fkey,
  DROP originating_cluster_id;
