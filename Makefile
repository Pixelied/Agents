.PHONY: test validate check

test:
	python -m unittest discover -s tests -v

validate:
	python agentctl.py validate

check: test validate
