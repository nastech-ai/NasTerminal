"""
NasTech Guardian — State Machine
Orchestrates the guardian agent lifecycle through well-defined states.
"""

import logging
from enum import Enum, auto
from typing import Callable, Dict, Optional

log = logging.getLogger(__name__)


class State(Enum):
    IDLE        = auto()
    HEALTH_CHECK = auto()
    BUILD       = auto()
    TEST        = auto()
    RELEASE     = auto()
    REPAIR      = auto()
    NOTIFY      = auto()
    ERROR       = auto()
    DONE        = auto()


TRANSITIONS: Dict[State, list] = {
    State.IDLE:         [State.HEALTH_CHECK, State.DONE],
    State.HEALTH_CHECK: [State.BUILD, State.REPAIR, State.ERROR],
    State.BUILD:        [State.TEST, State.REPAIR, State.ERROR],
    State.TEST:         [State.RELEASE, State.REPAIR, State.ERROR],
    State.RELEASE:      [State.NOTIFY, State.ERROR],
    State.REPAIR:       [State.HEALTH_CHECK, State.NOTIFY, State.ERROR],
    State.NOTIFY:       [State.IDLE, State.DONE],
    State.ERROR:        [State.NOTIFY, State.IDLE],
    State.DONE:         [],
}


class StateMachine:
    """Finite state machine that drives the NasTech Guardian agent loop."""

    def __init__(self, initial: State = State.IDLE):
        self.state   = initial
        self.history = [initial]
        self._handlers: Dict[State, Callable] = {}

    def register(self, state: State, handler: Callable):
        """Register a callable to run when entering a state."""
        self._handlers[state] = handler

    def transition(self, next_state: State) -> bool:
        allowed = TRANSITIONS.get(self.state, [])
        if next_state not in allowed:
            log.error("[state_machine] invalid transition %s → %s", self.state, next_state)
            return False
        log.info("[state_machine] %s → %s", self.state.name, next_state.name)
        self.state = next_state
        self.history.append(next_state)
        handler = self._handlers.get(next_state)
        if callable(handler):
            handler()
        return True

    def run_until_done(self, transitions: list):
        """Drive the machine through a pre-planned list of states."""
        for next_state in transitions:
            if not self.transition(next_state):
                self.transition(State.ERROR)
                break
            if self.state == State.DONE:
                break

    def summary(self) -> dict:
        return {
            "current": self.state.name,
            "history": [s.name for s in self.history],
        }


def main():
    sm = StateMachine()
    sm.run_until_done([
        State.HEALTH_CHECK,
        State.BUILD,
        State.TEST,
        State.RELEASE,
        State.NOTIFY,
        State.DONE,
    ])
    import json
    print(json.dumps(sm.summary(), indent=2))


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
