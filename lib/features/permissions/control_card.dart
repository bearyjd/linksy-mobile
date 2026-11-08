import 'package:flutter/material.dart';

/// Where the user is in setting up Control from your computer (the companion
/// app, Android's Accessibility switch, one pairing).
enum ControlStep { off, install, accessibility, pair, ready, active }

typedef CompanionState = ({bool installed, bool accessibility, bool paired});

ControlStep controlStep({
  required bool on,
  required CompanionState state,
  required bool active,
}) => ControlStep.off;

class ControlCard extends StatelessWidget {
  const ControlCard({super.key});

  @override
  Widget build(BuildContext context) => const SizedBox();
}
