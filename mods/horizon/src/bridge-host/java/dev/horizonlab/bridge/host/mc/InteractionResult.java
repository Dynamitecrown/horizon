package dev.horizonlab.bridge.host.mc;

public sealed interface InteractionResult permits InteractionResult.Success, InteractionResult.Pass {
    Success SUCCESS = new Success();
    Pass PASS = new Pass();

    final class Success implements InteractionResult {}

    final class Pass implements InteractionResult {}
}
