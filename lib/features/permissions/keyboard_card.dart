import 'package:flutter/material.dart';

import '../../app/linksy_controller.dart';
import '../../app/platform.dart';
import '../../app/scope.dart';
import '../../ui/components.dart';
import '../../ui/l10n.dart';
import '../../ui/sig.dart';

/// Where the user is in setting up Linksy Keyboard (Android has the user turn
/// it on in its settings, then pick it each time; nothing here can do either).
enum KeyboardStep {
  /// The switch is off.
  off,

  /// On here, but not turned on in Android's keyboard settings yet.
  enable,

  /// Turned on in Android; waiting for a computer to ask.
  ready,

  /// A computer is typing, but another keyboard is the one in use.
  switchTo,

  /// A computer is typing through Linksy Keyboard.
  active,
}

KeyboardStep keyboardStep({
  required bool on,
  required ({bool enabled, bool current, bool typing}) state,
}) {
  if (!on) return KeyboardStep.off;
  if (!state.enabled) return KeyboardStep.enable;
  if (!state.typing) return KeyboardStep.ready;
  return state.current ? KeyboardStep.active : KeyboardStep.switchTo;
}

/// "Type from your computer": lets a paired computer type into the field open
/// on this phone, through Linksy Keyboard. Part of the permissions sheet.
class KeyboardCard extends StatefulWidget {
  const KeyboardCard({super.key});

  @override
  State<KeyboardCard> createState() => _KeyboardCardState();
}

class _KeyboardCardState extends State<KeyboardCard> {
  @override
  void initState() {
    super.initState();
    // After the first frame: the controller tells its listeners.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) LinksyScope.read(context).refreshKeyboard();
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = Sig.of(context);
    final l = context.l10n;
    final LinksyController c = LinksyScope.of(context);
    final step = keyboardStep(on: c.keyboardOn, state: c.keyboard);
    final status = switch (step) {
      KeyboardStep.off => null,
      KeyboardStep.enable => l.keyboardStatusEnable,
      KeyboardStep.ready => l.keyboardStatusReady,
      KeyboardStep.switchTo => l.keyboardStatusSwitch,
      KeyboardStep.active => l.keyboardStatusActive,
    };
    return SigCard(
      color: s.bg,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const IconCircle(icon: Ph.keyboard, color: Sig.blue, size: 42),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l.keyboardCardTitle, style: sora(16, color: s.ink)),
                const SizedBox(height: 4),
                Text(
                  l.keyboardCardWhy,
                  style: TextStyle(fontSize: 13, color: s.muted, height: 1.4),
                ),
                if (status != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    status,
                    style: TextStyle(
                      fontSize: 13,
                      color: s.ink,
                      fontWeight: FontWeight.w600,
                      height: 1.4,
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    if (step == KeyboardStep.off)
                      SigButton(
                        label: l.keyboardTurnOn,
                        height: 40,
                        onTap: () => c.setKeyboard(true),
                      ),
                    if (step == KeyboardStep.enable)
                      SigButton(
                        label: l.keyboardOpenSettings,
                        height: 40,
                        onTap: LinksyPlatform.openKeyboardSettings,
                      ),
                    if (step == KeyboardStep.switchTo)
                      SigButton(
                        label: l.keyboardSwitch,
                        height: 40,
                        onTap: LinksyPlatform.showKeyboardPicker,
                      ),
                    if (step != KeyboardStep.off)
                      SigButton(
                        label: l.keyboardTurnOff,
                        kind: SigButtonKind.quiet,
                        height: 40,
                        onTap: () => c.setKeyboard(false),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
