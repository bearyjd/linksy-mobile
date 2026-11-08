import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:linksy/app/activity.dart';
import 'package:linksy/app/linksy_controller.dart';
import 'package:linksy/app/scope.dart';
import 'package:linksy/core/demo_node.dart';
import 'package:linksy/core/models.dart';
import 'package:linksy/features/permissions/keyboard_card.dart';
import 'package:linksy/features/sharing/sharing.dart';
import 'package:linksy/l10n/app_localizations.dart';
import 'package:linksy/ui/sig.dart';

/// A node that records what it announces and lets the test send it events.
class _Node extends DemoNode {
  _Node() : super(name: 'Test Phone', downloadDir: '/downloads');

  final told = <List<String>>[];
  final ended = <String>[];
  final events$ = StreamController<LinksyEvent>.broadcast();

  @override
  Stream<LinksyEvent> get events => events$.stream;

  @override
  Future<void> setFeatures(List<String> features) async => told.add(features);

  @override
  Future<void> endSession(String session) async => ended.add(session);
}

const _studio = DeviceInfo(
  id: 'ebb09ce2aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  name: 'Studio Desktop',
  kind: DeviceKind.desktop,
  os: 'linux',
);

void main() {
  final calls = <MethodCall>[];
  var keyboard = <String, bool>{};

  Future<(LinksyController, _Node)> start(WidgetTester tester) async {
    calls.clear();
    keyboard = {'enabled': false, 'current': false, 'typing': false};
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('app.linksy/platform'),
      (call) async {
        calls.add(call);
        return switch (call.method) {
          'prefs' => {'onboarded': '1'},
          'keyboardState' => keyboard,
          _ => null,
        };
      },
    );
    tester.view
      ..physicalSize = const Size(1080, 2400)
      ..devicePixelRatio = 2.625;
    addTearDown(tester.view.reset);
    final node = _Node();
    final c = LinksyController(node, ActivityStore(null));
    c.prefs = {'onboarded': '1'};
    await c.start();
    return (c, node);
  }

  Future<void> finish(WidgetTester tester, LinksyController c) async {
    await tester.pumpWidget(const SizedBox());
    c.dispose();
    await tester.pump(const Duration(seconds: 6));
  }

  test('the card walks through the steps Android needs', () {
    const idle = (enabled: false, current: false, typing: false);
    expect(keyboardStep(on: false, state: idle), KeyboardStep.off);
    expect(keyboardStep(on: true, state: idle), KeyboardStep.enable);
    const enabled = (enabled: true, current: false, typing: false);
    expect(keyboardStep(on: true, state: enabled), KeyboardStep.ready);
    const asked = (enabled: true, current: false, typing: true);
    expect(keyboardStep(on: true, state: asked), KeyboardStep.switchTo);
    const using = (enabled: true, current: true, typing: true);
    expect(keyboardStep(on: true, state: using), KeyboardStep.active);
    // A keyboard in use with nobody typing is just ready.
    const quiet = (enabled: true, current: true, typing: false);
    expect(keyboardStep(on: true, state: quiet), KeyboardStep.ready);
    // Off is off whatever Android says.
    expect(keyboardStep(on: false, state: using), KeyboardStep.off);
  });

  testWidgets('keyboard is announced only when it is on and Android has it', (
    tester,
  ) async {
    final (c, node) = await start(tester);
    expect(c.keyboardOn, isFalse);

    // On, but not yet enabled in Android's settings: not announced.
    await c.setKeyboard(true);
    expect(c.keyboardOn, isTrue);
    expect(node.told.last, isNot(contains('keyboard')));

    // The user enables it there and comes back.
    keyboard['enabled'] = true;
    await c.refreshKeyboard();
    expect(node.told.last, contains('keyboard'));

    // Turned off in the app: gone from the list again.
    await c.setKeyboard(false);
    expect(node.told.last, isNot(contains('keyboard')));
    expect(c.prefs['keyboard'], isNull);

    // Android disabling it takes it away too, even with the switch on.
    await c.setKeyboard(true);
    expect(node.told.last, contains('keyboard'));
    keyboard['enabled'] = false;
    await c.refreshKeyboard();
    expect(node.told.last, isNot(contains('keyboard')));
    await finish(tester, c);
  });

  testWidgets('a computer typing goes to the keyboard, not to the sharing '
      'service or the touchpad', (tester) async {
    final (c, node) = await start(tester);
    node.events$.add(
      const SessionStarted(
        session: 'type-1',
        peer: _studio,
        kind: SessionKind.input,
        sharing: true,
      ),
    );
    await tester.pump();
    final start1 = calls.where((m) => m.method == 'startTyping').single;
    expect(start1.arguments, {'session': 'type-1', 'peer': 'Studio Desktop'});
    expect(calls.where((m) => m.method == 'startShare'), isEmpty);
    expect(c.typing.keys, ['type-1']);
    expect(c.remotes, isEmpty);

    // This phone driving a computer's touchpad is still its own thing.
    node.events$.add(
      const SessionStarted(
        session: 'pad-1',
        peer: _studio,
        kind: SessionKind.input,
        sharing: false,
      ),
    );
    await tester.pump();
    expect(c.remotes[_studio.id], 'pad-1');
    expect(c.typing.keys, ['type-1']);

    node.events$.add(const SessionEnded('type-1'));
    await tester.pump();
    expect(c.typing, isEmpty);
    await c.stopTyping('type-1');
    expect(node.ended, ['type-1']);
    await finish(tester, c);
  });

  testWidgets('the card: turn on, open Android settings, switch', (
    tester,
  ) async {
    final (c, _) = await start(tester);
    await tester.pumpWidget(
      LinksyScope(
        controller: c,
        child: MaterialApp(
          theme: buildTheme(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: SingleChildScrollView(child: KeyboardCard())),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Type from your computer'), findsOneWidget);
    expect(find.text('Turn on'), findsOneWidget);

    await tester.tap(find.text('Turn on'));
    await tester.pumpAndSettle();
    expect(c.keyboardOn, isTrue);
    expect(find.text('Open keyboard settings'), findsOneWidget);
    expect(find.textContaining('Turn on Linksy Keyboard'), findsOneWidget);

    await tester.tap(find.text('Open keyboard settings'));
    await tester.pumpAndSettle();
    expect(calls.map((m) => m.method), contains('openKeyboardSettings'));

    // Enabled in Android, then a computer asks to type.
    keyboard
      ..['enabled'] = true
      ..['typing'] = true;
    await c.refreshKeyboard();
    await tester.pumpAndSettle();
    expect(find.textContaining('Switch to Linksy Keyboard'), findsOneWidget);
    await tester.tap(find.text('Switch keyboard'));
    await tester.pumpAndSettle();
    expect(calls.map((m) => m.method), contains('showKeyboardPicker'));

    keyboard['current'] = true;
    await c.refreshKeyboard();
    await tester.pumpAndSettle();
    expect(find.textContaining('is typing through Linksy Keyboard'), findsOneWidget);

    await tester.tap(find.text('Turn off'));
    await tester.pumpAndSettle();
    expect(c.keyboardOn, isFalse);
    expect(find.text('Turn on'), findsOneWidget);
    await finish(tester, c);
  });

  testWidgets('the prompt says the computer wants to type, every time', (
    tester,
  ) async {
    final (c, _) = await start(tester);
    await tester.pumpWidget(
      LinksyScope(
        controller: c,
        child: MaterialApp(
          theme: buildTheme(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(
            body: SessionRequestDialog(
              request: SessionRequest(
                request: 'r1',
                from: _studio,
                kind: SessionKind.input,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Studio Desktop wants to type on this phone'), findsOneWidget);
    expect(find.textContaining('touchpad'), findsNothing);
    expect(find.text('Allow'), findsOneWidget);
    await finish(tester, c);
  });
}
