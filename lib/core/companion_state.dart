/// What Android says about the Linksy Control companion app now.
///
/// [configured]: this build pins a companion to trust (and Android can check
/// who signed it). [installed] already means "installed and signed by that
/// pin". [bridged]: a computer's control session is running through it now.
typedef CompanionState = ({
  bool configured,
  bool installed,
  bool accessibility,
  bool paired,
  bool bridged,
});
