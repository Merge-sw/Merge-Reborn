package com.merge.merge.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduling infrastructure scoped to the session package.
 * Keeping it here rather than on MergeApplication means scheduling concerns are
 * owned by the module that needs them.
 */
@Configuration
@EnableScheduling
class SessionSchedulingConfig {
}
