-- Encrypted transaction history for the web dashboard (Approach B).
--
-- The relay stays end-to-end blind: it stores ONLY the AES-256-GCM ciphertext (`blob`), a content
-- hash for idempotent inserts, and the server receipt time. It can never read amount/content — the
-- room secret never reaches the Worker. The web dashboard pulls these rows (signed with the room
-- macKey), decrypts each blob on-device, and rebuilds the full history + reports client-side.
--
--   room  the roomId the transaction belongs to.
--   hash  SHA-256(blob) hex — PK with room, so a hub WorkManager retry (identical blob) is a no-op.
--   blob  the base64url ciphertext, byte-for-byte what /send fanned out over FCM.
--   ts    server receipt time (epoch ms). Used only for pagination + retention; the real transaction
--         time lives inside the (encrypted) payload and is recovered by the web after decrypt.
CREATE TABLE IF NOT EXISTS tx (
  room TEXT    NOT NULL,
  hash TEXT    NOT NULL,
  blob TEXT    NOT NULL,
  ts   INTEGER NOT NULL,
  PRIMARY KEY (room, hash)
);

-- Newest-first pagination + retention prune both query (room, ts).
CREATE INDEX IF NOT EXISTS idx_tx_room_ts ON tx (room, ts);
