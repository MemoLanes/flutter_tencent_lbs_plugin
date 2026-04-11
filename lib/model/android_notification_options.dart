class AndroidNotificationOptions {
  AndroidNotificationOptions({
    required this.id,
    required this.channelId,
    required this.channelName,
    required this.notificationTitle,
    this.notificationText,
    this.channelDescription,
    this.enableVibration = false,
    this.playSound = false,
    this.showWhen = false,
  })  : assert(channelId.isNotEmpty),
        assert(channelName.isNotEmpty),
        assert(notificationTitle.isNotEmpty);

  final int id;
  final String channelId;
  final String channelName;
  final String? channelDescription;
  final String notificationTitle;
  final String? notificationText;
  final bool enableVibration;
  final bool playSound;
  final bool showWhen;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'channelId': channelId,
      'channelName': channelName,
      'channelDescription': channelDescription,
      'enableVibration': enableVibration,
      'notificationTitle': notificationTitle,
      'notificationText': notificationText,
      'playSound': playSound,
      'showWhen': showWhen,
    };
  }
}
