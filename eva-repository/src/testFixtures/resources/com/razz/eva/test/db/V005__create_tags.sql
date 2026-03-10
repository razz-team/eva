CREATE TABLE tag (
  subject_id        UUID                NOT NULL,
  name              TEXT                NOT NULL,
  value             TEXT                NOT NULL,

  PRIMARY KEY (subject_id, name)
);
