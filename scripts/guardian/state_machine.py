"""
NasTech Guardian — State Machine
Drives the guardian agent lifecycle through well-defined states.
"""

import logging
from enum import Enum, auto
from typing import Callable, Dict, List, Optional

log = logging.getLogger(__name__)


class State(Enum):
    IDLE         = auto()
    VERIFY       = auto()
    IDENTITY     = auto()
    DEPENDENCY   = auto()
    HEALTH       = auto()
    BUILD        = auto()
    REPAIR       = auto()
    RELEASE      = auto()
    NOTIFY       = auto()
    ERROR        = auto()
    DONE         = auto()


TRANSITIONS: Dict[State, List[State]] = {
    State.IDLE:       [State.VERIFY, State.DONE],
    State.VERIFY:     [State.IDENTITY, State.ERROR],
    State.IDENTITY:   [State.DEPENDENCY, State.ERROR],
    State.DEPENDENCY: [State.HEALTH, State.ERROR],
    State.HEALTH:     [State.BUILD, State.REPAIR, State.ERROR],
    State.BUILD:      [State.RELEASE, State.REPAIR, State.ERROR],
    State.REPAIR:     [State.BUILD, State.NOTIFY, State.ERROR],
    State.RELEASE:    [State.NOTIFY, State.ERROR],
    State.NOTIFY:     [State.IDLE, State.DONE],
    State.ERROR:      [State.NOTIFY, State.IDLE],
    State.DONE:       [],
}

DEFAULT_PIPELINE: List[State] = [
    State.VERIFY,
    State.IDENTITY,
    State.DEPENDENCY,
    State.HEALTH,
    State.BUILD,
    State.RELEASE,
    State.NOTIFY,
    State.DONE,
]


class StateMachine:
    """Finite state machine that drives the NasTech Guardian agent loop."""

    def __init__(self, initial: State = State.IDLE):
        self.state   = initial
        self.history: List[State] = [initial]
        self._handlers: Dict[State, Callable] = {}

    def register(self, state: State, handler: Callable) -> None:
        self._handlers[state] = handler

    def transition(self, next_state: State) -> bool:
        allowed = TRANSITIONS.get(self.state, [])
        if next_state not in allowed:
            log.error("[state_machine] invalid transition %s → %s",
                      self.state.name, next_state.name)
            return False
        log.info("[state_machine] %s → %s", self.state.name, next_state.name)
        self.state = next_state
        self.history.append(next_state)
        handler = self._handlers.get(next_state)
        if callable(handler):
            handler()
        return True

    def run_pipeline(self, pipeline: Optional[List[State]] = None) -> bool:
        """Drive the machine through a list of states. Returns True if DONE."""
        for next_state in (pipeline or DEFAULT_PIPELINE):
            if not self.transition(next_state):
                self.state = State.ERROR
                self.history.append(State.ERROR)
                return False
            if self.state == State.DONE:
                return True
        return self.state == State.DONE

    def summary(self) -> dict:
        return {
            "current": self.state.name,
            "history": [s.name for s in self.history],
            "done":    self.state == State.DONE,
        }


def main() -> None:
    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")
    sm = StateMachine()
    success = sm.run_pipeline()
    import json
    print(json.dumps(sm.summary(), indent=2))
    return 0 if success else 1


if __name__ == "__main__":
    import sys
    sys.exit(main())
