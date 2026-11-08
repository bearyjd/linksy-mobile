import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:linksy/app/activity.dart';
import 'package:linksy/app/linksy_controller.dart';
import 'package:linksy/app/scope.dart';
import 'package:linksy/core/demo_node.dart';
import 'package:linksy/core/models.dart';
import 'package:linksy/features/permissions/control_card.dart';
import 'package:linksy/l10n/app_localizations.dart';
import 'package:linksy/ui/sig.dart';

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
  var state = <String, bool>{};

  Future<(LinksyController, _Node)> start(WidgetTester tester) async {
    calls.clear();
    state = {'installed': false, 'accessibility': false, 'paired': false};
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('app.linksy/platform'),
      (call) async {
        calls.add(call);
        return switch (call.method) {
          'prefs' => {'onboarded': '1'},
          'controlState' => state,
          'pairCompanion' => true,
          'openUrl' => true,
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
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
    return (c, node);
  }

  Future<void> finish(WidgetTester tester, LinksyController c) async {
    await tester.pumpWidget(const SizedBox());
    c.dispose();
    await tester.pump(const Duration(seconds: 6));
  }

  Future<void> showCard(WidgetTester tester, LinksyController c) async {
    await tester.pumpWidget(
      LinksyScope(
        controller: c,
        child: MaterialApp(
          theme: buildTheme(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(
            body: SingleChildScrollView(child: ControlCard()),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  test('the card walks through the steps in order', () {
    CompanionState s(bool i, bool a, bool p) =>
        (installed: i, accessibility: a, paired: p);
    expect(
      controlStep(on: false, state: s(true, true, true), active: false),
      ControlStep.off,
    );
    expect(
      controlStep(on: true, state: s(false, false, false), active: false),
      ControlStep.install,
    );
    expect(
      controlStep(on: true, state: s(true, false, false), active: false),
      ControlStep.accessibility,
    );
    expect(
      controlStep(on: true, state: s(true, true, false), active: false),
      ControlStep.pair,
    );
    expect(
      controlStep(on: true, state: s(true, true, true), active: false),
      ControlStep.ready,
    );
    expect(
      controlStep(on: true, state: s(true, true, true), active: true),
      ControlStep.active,
    );
    // Uninstalled while paired: back to install; a session never shows
    // as active without everything in place.
    expect(
      controlStep(on: true, state: s(false, true, true), active: true),
      ControlStep.install,
    );
    expect(
      controlStep(on: false, state: s(true, true, true), active: true),
      ControlStep.off,
    );
  });

  testWidgets('control is announced only when on, installed, accessibility '
      'is on and paired', (tester) async {
    final (c, node) = await start(tester);
    expect(c.controlOn, isFalse);
    expect(node.told.expand((f) => f), isNot(contains('control')));

    // Everything in place, but the user has not turned it on.
    state = {'installed': true, 'accessibility': true, 'paired': true};
    await c.refreshControl();
    expect(node.told.last, isNot(contains('control')));

    await c.setControl(true);
    expect(c.controlOn, isTrue);
    expect(node.told.last, contains('control'));

    // The companion is uninstalled, or Accessibility turned off.
    state['accessibility'] = false;
    await c.refreshControl();
    expect(node.told.last, isNot(contains('control')));
    state['accessibility'] = true;
    await c.refreshControl();
    expect(node.told.last, contains('control'));
    state['paired'] = false;
    await c.refreshControl();
    expect(node.told.last, isNot(contains('control')));
    state['paired'] = true;
    state['installed'] = false;
    await c.refreshControl();
    expect(node.told.last, isNot(contains('control')));
    state['installed'] = true;
    await c.refreshControl();

    // The switch off wins over everything.
    await c.setControl(false);
    expect(node.told.last, isNot(contains('control')));
    expect(c.prefs['control'], isNull);
    await finish(tester, c);
  });

  testWidgets('refreshing without a change does not announce again', (
    tester,
  ) async {
    final (c, node) = await start(tester);
    state = {'installed': true, 'accessibility': true, 'paired': true};
    await c.setControl(true);
    final before = node.told.length;
    await c.refreshControl();
    expect(node.told.length, before);
    await finish(tester, c);
  });

  testWidgets('an accepted control session starts the screen share, which '
      'binds the companion on the Android side', (tester) async {
    final (c, node) = await start(tester);
    state = {'installed': true, 'accessibility': true, 'paired': true};
    await c.setControl(true);
    calls.clear();
    node.events$.add(
      const SessionStarted(
        session: 'ctl-1',
        peer: _studio,
        kind: SessionKind.control,
        sharing: true,
      ),
    );
    await tester.pump();
    final share = calls.where((m) => m.method == 'startShare').single;
    expect(share.arguments['kind'], 'control');
    expect(share.arguments['session'], 'ctl-1');
    await finish(tester, c);
  });

  testWidgets('a control session that arrives after the switch went off or '
      'the companion went away is ended, never started', (tester) async {
    final (c, node) = await start(tester);
    state = {'installed': true, 'accessibility': true, 'paired': true};
    await c.setControl(true);
    await c.setControl(false);
    calls.clear();
    node.events$.add(
      const SessionStarted(
        session: 'late-1',
        peer: _studio,
        kind: SessionKind.control,
        sharing: true,
      ),
    );
    await tester.pump();
    expect(calls.where((m) => m.method == 'startShare'), isEmpty);
    expect(node.ended, ['late-1']);
    await finish(tester, c);
  });

  testWidgets('turning it off ends a running control session at once, and '
      'leaves a plain screen share alone', (tester) async {
    final (c, node) = await start(tester);
    state = {'installed': true, 'accessibility': true, 'paired': true};
    await c.setControl(true);
    for (final (id, kind) in [
      ('ctl-1', SessionKind.control),
      ('scr-1', SessionKind.screen),
    ]) {
      node.events$.add(
        SessionStarted(session: id, peer: _studio, kind: kind, sharing: true),
      );
    }
    await tester.pump();
    calls.clear();
    await c.setControl(false);
    final stops = calls.where((m) => m.method == 'stopShare').toList();
    expect(stops.map((m) => m.arguments['session']), ['ctl-1']);
    expect(node.ended, ['ctl-1']);
    expect(node.told.last, isNot(contains('control')));
    await finish(tester, c);
  });

  testWidgets('a plain screen share is not affected', (tester) async {
    final (c, node) = await start(tester);
    calls.clear();
    node.events$.add(
      const SessionStarted(
        session: 'scr-1',
        peer: _studio,
        kind: SessionKind.screen,
        sharing: true,
      ),
    );
    await tester.pump();
    expect(calls.where((m) => m.method == 'startShare'), hasLength(1));
    expect(node.ended, isEmpty);
    await finish(tester, c);
  });

  testWidgets('the card: install, accessibility, pair, ready', (tester) async {
    final (c, _) = await start(tester);
    await showCard(tester, c);
    expect(find.text('Control from your computer'), findsOneWidget);
    expect(find.text('Turn on'), findsOneWidget);

    await tester.tap(find.text('Turn on'));
    await tester.pumpAndSettle();
    expect(c.controlOn, isTrue);
    // Not installed: how to get it, and the plain words about a refusal.
    expect(find.text('How to install it'), findsOneWidget);
    expect(find.textContaining('Play Protect'), findsOneWidget);
    await tester.tap(find.text('How to install it'));
    await tester.pumpAndSettle();
    final open = calls.where((m) => m.method == 'openUrl').single;
    expect(open.arguments['url'], 'https://github.com/bearyjd/linksy-control');

    state['installed'] = true;
    await c.refreshControl();
    await tester.pumpAndSettle();
    expect(find.text('Open Accessibility settings'), findsOneWidget);
    expect(find.text('Open app info'), findsOneWidget);
    await tester.tap(find.text('Open Accessibility settings'));
    await tester.tap(find.text('Open app info'));
    await tester.pumpAndSettle();
    final methods = calls.map((m) => m.method);
    expect(methods, contains('openAccessibilitySettings'));
    expect(methods, contains('openCompanionInfo'));

    state['accessibility'] = true;
    await c.refreshControl();
    await tester.pumpAndSettle();
    expect(find.text('Pair with Linksy'), findsOneWidget);
    await tester.tap(find.text('Pair with Linksy'));
    await tester.pumpAndSettle();
    expect(calls.map((m) => m.method), contains('pairCompanion'));

    state['paired'] = true;
    await c.refreshControl();
    await tester.pumpAndSettle();
    expect(find.textContaining('Ready'), findsOneWidget);

    await tester.tap(find.text('Turn off'));
    await tester.pumpAndSettle();
    expect(c.controlOn, isFalse);
    await finish(tester, c);
  });
}
