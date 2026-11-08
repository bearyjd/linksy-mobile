import 'package:flutter/material.dart';

import '../../app/linksy_controller.dart';
import '../../app/platform.dart';
import '../../app/scope.dart';
import '../../core/companion_state.dart';
import '../../ui/components.dart';
import '../../ui/l10n.dart';
import '../../ui/sig.dart';

/// Where the companion app's page explains how to install it.
const controlCompanionUrl = 'https://github.com/bearyjd/linksy-control';

/// Where the user is in setting up Control from your computer: the separate
/// companion app, its Accessibility switch (Android makes the user turn it on
/// themselves), one pairing.
enum ControlStep { off, unconfigured, install, accessibility, pair, ready, active }

ControlStep controlStep({
  required bool on,
  required CompanionState state,
  required bool active,
}) {
  if (!on) return ControlStep.off;
  if (!state.installed) return ControlStep.install;
  if (!state.accessibility) return ControlStep.accessibility;
  if (!state.paired) return ControlStep.pair;
  return active ? ControlStep.active : ControlStep.ready;
}

/// "Control from your computer": lets a computer tap, swipe and type on this
/// phone through the Linksy Control companion app. Part of the permissions
/// sheet; nothing here touches the phone until the user turns it on.
class ControlCard extends StatefulWidget {
  const ControlCard({super.key});

  @override
  State<ControlCard> createState() => _ControlCardState();
}

class _ControlCardState extends State<ControlCard> {
  @override
  void initState() {
    super.initState();
    // After the first frame: the controller tells its listeners.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) LinksyScope.read(context).refreshControl();
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = Sig.of(context);
    final l = context.l10n;
    final LinksyController c = LinksyScope.of(context);
    final step = controlStep(
      on: c.controlOn,
      state: c.companion,
      active: c.controlling,
    );
    final status = switch (step) {
      ControlStep.off => null,
      ControlStep.unconfigured => 'Not available in this build.',
      ControlStep.install => l.controlStatusInstall,
      ControlStep.accessibility => l.controlStatusAccessibility,
      ControlStep.pair => l.controlStatusPair,
      ControlStep.ready => l.controlStatusReady,
      ControlStep.active => l.controlStatusActive,
    };
    final refused =
        step == ControlStep.install || step == ControlStep.accessibility;
    return SigCard(
      color: s.bg,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const IconCircle(icon: Ph.cursorClick, color: Sig.blue, size: 42),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l.controlCardTitle, style: sora(16, color: s.ink)),
                const SizedBox(height: 4),
                Text(
                  l.controlCardWhy,
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
                if (refused) ...[
                  const SizedBox(height: 8),
                  Text(
                    l.controlRefusal,
                    style: TextStyle(fontSize: 13, color: s.muted, height: 1.4),
                  ),
                ],
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    if (step == ControlStep.off)
                      SigButton(
                        label: l.controlTurnOn,
                        height: 40,
                        onTap: () => c.setControl(true),
                      ),
                    if (step == ControlStep.install)
                      SigButton(
                        label: l.controlHowToInstall,
                        height: 40,
                        onTap: () =>
                            LinksyPlatform.openUrl(controlCompanionUrl),
                      ),
                    if (step == ControlStep.accessibility) ...[
                      SigButton(
                        label: l.controlOpenAccessibility,
                        height: 40,
                        onTap: LinksyPlatform.openAccessibilitySettings,
                      ),
                      SigButton(
                        label: l.controlOpenAppInfo,
                        kind: SigButtonKind.secondary,
                        height: 40,
                        onTap: LinksyPlatform.openCompanionInfo,
                      ),
                    ],
                    if (step == ControlStep.pair)
                      SigButton(
                        label: l.controlPair,
                        height: 40,
                        onTap: c.pairCompanion,
                      ),
                    if (step != ControlStep.off)
                      SigButton(
                        label: l.controlTurnOff,
                        kind: SigButtonKind.quiet,
                        height: 40,
                        onTap: () => c.setControl(false),
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
