STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
PROD_OUTPUTS_DIR := $(STEPS_DIR)/prod/outputs
TEMP_DIR := $(shell mktemp -d)

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	mkdir -p $(TEST_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/hopping-windows/ksql.yml $(TEMP_DIR)
	diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-transient-window.log $(DEV_OUTPUTS_DIR)/transient-window/output-0.log
	diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-transient-query.log $(DEV_OUTPUTS_DIR)/transient-query/output-0.log
