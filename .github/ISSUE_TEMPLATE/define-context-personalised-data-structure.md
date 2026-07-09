---
name: "Define Context.personalisedData structure for AI Orchestration"
about: "Specify and implement the schema for Context.personalisedData to enable proper prompt personalization in MISSION_GENERATE"
title: "spec: Define Context.personalisedData structure"
labels: "gating, practice, ai-orchestration"
assignees: ""
---

### Problem Description
In the AI Orchestration module, the `MISSION_GENERATE` action (triggered asynchronously when a Drill or Build attempt fails) is designed to generate a remedial "mission" for the student by blending their personalized context data into the Gemini prompt. 

Currently, the schema and internal structure of `Context.personalisedData` is undefined. It is treated in the codebase as an opaque, unstructured JSON blob (`Map<String, Object>`), which limits the ability of the prompt builder to parse and systematically weigh specific student metrics (e.g., specific concept strengths, preferred cognitive level, novelty tolerance, consistency scores).

### Rationale
To generate highly relevant, structured remedial missions, the Gemini prompt builder needs access to a structured and validated model of `Context.personalisedData`. Having a concrete schema will allow:
1. Extraction of granular fields such as `LevelOfThinking`, `PreferredLanguage`, and `CompetencyData`.
2. Safe validation of inputs at the API / database boundaries.
3. Enhanced prompting strategies that translate specific performance deficits directly into specialized remedial tasks.

### Proposed Changes
1. **Design Schema**: Define the fields, types, and nested structure of `personalisedData`.
2. **Database Migration**: Update the `Context` MongoDB model in the `identity` module to bind this structured data instead of/or inside the current dynamic map.
3. **Prompt Update**: Modify `InstructorServiceImpl.buildPrompt()` for `MISSION_GENERATE` to parse the structured fields and output a highly tailored system instructions block.
4. **Validation Integration**: Ensure proper Jakarta Validation annotations are applied to the incoming requests.

### Acceptance Criteria
- [ ] Schema document reviewed and approved by Product/Engineering.
- [ ] `Context.personalisedData` model updated in `com.merge.merge.identity.models` with full test coverage.
- [ ] Prompt builder in `InstructorServiceImpl` updated to use the structured fields instead of `.toString()`.
- [ ] Verification tests added to `InstructorServiceTest` asserting that the personalized fields are correctly blended into the generated prompt.
