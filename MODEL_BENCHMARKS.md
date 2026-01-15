# Model Performance Benchmarks

**Date:** 2026-01-15
**Methodology:** Bowling Kata TDD Testbench
**Testbench:** [bowling-kata](https://github.com/.../bowling-kata)

## Overview

jwright performance varies significantly based on the LLM model used. We evaluated 11 Ollama models using a standardized bowling scoring kata with 12 progressive test cases.

### Why Bowling?

The bowling kata is ideal for LLM evaluation because:
- **Progressive complexity** - Tests build from trivial to complex
- **Clear success criteria** - Each test has an exact expected value
- **Domain logic** - Requires understanding rules, not just syntax
- **State management** - Strikes/spares require tracking across frames

## Test Cases

| Level | Test | Description | Complexity |
|-------|------|-------------|------------|
| **Basic** |
| T1 | New game | score() returns 0 | Return statement |
| T2 | One roll | [5] → 5 | Simple sum |
| T3 | Two rolls | [5,4] → 9 | Loop/accumulation |
| T4 | Two frames | [5,4,3,2] → 14 | Multiple frames |
| **Intermediate** |
| T5 | Spare | [5,5,3,0] → 16 | Spare bonus logic |
| T6 | Strike | [10,3,4] → 24 | Strike bonus logic |
| T7 | Two strikes | [10,10,3,4] → 47 | Cascading bonuses |
| **Advanced** |
| T8 | 10th spare | [0]x18,[5,5,3] → 13 | 10th frame spare rule |
| T9 | 10th strike | [0]x18,[10,3,4] → 17 | 10th frame strike rule |
| T10 | Perfect game | [10]x12 → 300 | All strikes cascade |
| T11 | Gutter game | [0]x20 → 0 | Edge case |
| T12 | Alternating | strike,spare,... → 200 | Complex pattern |

## Results

### Final Rankings

| Rank | Model | Size | Max Level | Time | Notes |
|------|-------|------|-----------|------|-------|
| 🥇 | **cogito:8b-8k** | 4.9 GB | 12/12 | 258s | Complete mastery |
| 🥈 | DeepSeek-R1-Qwen3-8B | 8.7 GB | 8/12 | 354s | Failed 10th frame strike |
| 🥉 | qwen2.5-coder:14b | 9.0 GB | 7/12 | 242s | Failed 10th frame spare |
| 4 | phi4-mini:latest | 2.5 GB | 7/12* | 141s | Inconsistent on re-run |
| 5 | mistral-nemo:latest-8k | 7.1 GB | 7/12* | 123s | Inconsistent on re-run |
| 6 | mistral-small3.1:latest | 15 GB | 7/12* | 135s | Inconsistent on re-run |
| 7 | qwen3:14b | 9.3 GB | 5/12 | 174s | Failed strike logic |
| 8 | rnj-1:latest | 5.1 GB | 5/12 | 151s | Failed strike logic |
| 9 | gemma3:12b-16k | 8.1 GB | 4/12 | 125s | Failed spare logic |
| 10 | phi4:latest | 9.1 GB | 1/12 | 36s | Failed basic summing |
| 11 | cogito:8b | 4.9 GB | 1/12 | 37s | Failed basic summing |

*Models showed variability between runs - passed T1-T7 in one run but failed earlier tests in subsequent runs.

### Detailed Results

#### Round 1 (T1-T7)

| Model | Size | T1 | T2 | T3 | T4 | T5 | T6 | T7 | Time | Max |
|-------|------|----|----|----|----|----|----|-----|------|-----|
| qwen2.5-coder:14b | 9.0 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 121s | 7 |
| cogito:8b-8k | 4.9 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 135s | 7 |
| phi4:latest | 9.1 GB | ✓ | ✗ | - | - | - | - | - | 36s | 1 |
| phi4-mini:latest | 2.5 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 141s | 7 |
| qwen3:14b | 9.3 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | - | 174s | 5 |
| gemma3:12b-16k | 8.1 GB | ✓ | ✓ | ✓ | ✓ | ✗ | - | - | 125s | 4 |
| mistral-small3.1:latest | 15 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 135s | 7 |
| mistral-nemo:latest-8k | 7.1 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 123s | 7 |
| cogito:8b | 4.9 GB | ✓ | ✗ | - | - | - | - | - | 37s | 1 |
| rnj-1:latest | 5.1 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | - | 151s | 5 |
| DeepSeek-R1-Qwen3-8B | 8.7 GB | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 131s | 7 |

#### Round 2 (T8-T12) - Advanced Tests

Only models that passed T1-T7:

| Model | Size | T8 | T9 | T10 | T11 | T12 | Time | Max |
|-------|------|----|----|-----|-----|-----|------|-----|
| **cogito:8b-8k** | 4.9 GB | ✓ | ✓ | ✓ | ✓ | ✓ | 258s | **12** |
| DeepSeek-R1-Qwen3-8B | 8.7 GB | ✓ | ✗ | - | - | - | 354s | 8 |
| qwen2.5-coder:14b | 9.0 GB | ✗ | - | - | - | - | 242s | 7 |
| phi4-mini:latest | 2.5 GB | ✗ | - | - | - | - | 128s | 4 |
| mistral-nemo:latest-8k | 7.1 GB | ✗ | - | - | - | - | 109s | 4 |
| mistral-small3.1:latest | 15 GB | ✗ | - | - | - | - | 27s | 1 |

## Key Findings

### 1. Size Doesn't Determine Performance

**cogito:8b-8k (4.9 GB)** outperformed **mistral-small3.1 (15 GB)** - the smallest successful model beat the largest.

### 2. Context Window Matters Significantly

**cogito:8b-8k** (8k context) dramatically outperformed **cogito:8b** (standard context):
- Same model weights, different context handling
- 8k version: 12/12 tests
- Standard version: 1/12 tests

### 3. LLM Output Variability

Several models passed T1-T7 in Round 1 but failed earlier tests in Round 2. This highlights the non-deterministic nature of LLM code generation. Production systems should include verification and retry logic.

### 4. Common Failure Patterns

| Failure Point | Likely Issue |
|---------------|--------------|
| T1-T2 | Model not generating valid Java syntax |
| T5 | Doesn't understand spare bonus concept |
| T6 | Doesn't understand strike bonus concept |
| T7 | Can't handle cascading/overlapping bonuses |
| T8-T9 | Doesn't handle 10th frame special rules |
| T10 | Perfect game exposes edge cases in bonus calc |
| T12 | Complex state tracking failure |

## Winning Implementation

The winning model (cogito:8b-8k) generated this clean implementation:

```java
package org.example;

public class BowlingGame {
    private int[] rolls = new int[21];
    private int currentRoll = 0;

    public void roll(int pins) {
        rolls[currentRoll++] = pins;
    }

    public int score() {
        int score = 0;
        int rollIndex = 0;

        for (int frame = 0; frame < 10; frame++) {
            if (isStrike(rollIndex)) {
                score += 10 + strikeBonus(rollIndex);
                rollIndex++;
            } else if (isSpare(rollIndex)) {
                score += 10 + spareBonus(rollIndex);
                rollIndex += 2;
            } else {
                score += sumOfBallsInFrame(rollIndex);
                rollIndex += 2;
            }
        }
        return score;
    }

    private boolean isStrike(int rollIndex) {
        return rolls[rollIndex] == 10;
    }

    private boolean isSpare(int rollIndex) {
        return rolls[rollIndex] + rolls[rollIndex + 1] == 10;
    }

    private int strikeBonus(int rollIndex) {
        return rolls[rollIndex + 1] + rolls[rollIndex + 2];
    }

    private int spareBonus(int rollIndex) {
        return rolls[rollIndex + 2];
    }

    private int sumOfBallsInFrame(int rollIndex) {
        return rolls[rollIndex] + rolls[rollIndex + 1];
    }
}
```

## Recommendations

### For jwright Users

1. **Best choice:** Use **cogito:8b-8k** for reliable code generation
2. **Alternative:** **qwen2.5-coder:14b** for general use (good up to T7 complexity)
3. **Budget option:** **phi4-mini:latest** (2.5 GB) handles basic to intermediate tasks

### For Complex Logic

- Prefer models with larger context windows
- Implement retry logic with test verification
- Use `@JwrightHint` annotations to guide generation

### Model Selection Guide

| Complexity | Recommended Model | Notes |
|------------|-------------------|-------|
| Simple methods | phi4-mini (2.5 GB) | Fast, small |
| Standard TDD | qwen2.5-coder:14b | Good balance |
| Complex algorithms | cogito:8b-8k | Best reliability |

## Running Your Own Benchmarks

Use the [bowling-kata testbench](https://github.com/.../bowling-kata) to evaluate new models:

```bash
# Test a single model
./test_model.sh <model-name>

# Test all configured models
./test_all_models.sh
```

See the testbench README for setup instructions.

---

*Generated using the bowling-kata testbench for jwright model evaluation*
