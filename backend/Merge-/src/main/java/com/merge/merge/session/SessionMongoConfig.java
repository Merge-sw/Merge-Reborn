package com.merge.merge.session;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers UUID representation so that UUID fields (studentId, conceptId, session id)
 * round-trip correctly as standard UUID strings (ISO/IEC 11578) rather than legacy
 * binary subtype 3.  Without this the MongoDB codec throws at encode time.
 */
@Configuration
class SessionMongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer uuidRepresentationCustomizer() {
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
