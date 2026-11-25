# Runtime event sequence

1. Byte Buddy advice calls the runtime bridge when a monitored JDK API is entered.
2. The application thread collects stack frames and argument metadata, then computes the rule score.
3. In Block mode, a rule score of at least 80 is enforceable. A score from 60 through 79 requires confirmation from a configured ML endpoint.
4. The application thread queues the event. It throws the block exception if enforcement was selected.
5. The worker preserves the synchronous decision or enriches the event asynchronously, then writes the audit record.
6. The optional dashboard reads and presents recorded outcomes.
