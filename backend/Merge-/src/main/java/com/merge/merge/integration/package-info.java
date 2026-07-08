/**
 * Integration — external clients not owned by any single ticket: Judge0, GitHub, Gemini, and the job queue.
 * Not a ticket itself; the job queue client is blocked on PRD §7.7 (BullMQ/Java runtime mismatch, open and blocking).
 */
package com.merge.merge.integration;
