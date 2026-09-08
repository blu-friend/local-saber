import 'package:flutter/material.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:local_saber/data/prefs.dart';
import 'package:local_saber/data/tools/pen.dart';
import 'package:local_saber/i18n/strings.g.dart';

class Pencil extends Pen {
  new()
    : super(
        name: t.editor.pens.pencil,
        sizeMin: 1,
        sizeMax: 15,
        sizeStep: 1,
        icon: pencilIcon,
        options: stows.lastPencilOptions.value,
        pressureEnabled: true,
        color: Color(stows.lastPencilColor.value),
        toolId: .pencil,
      );

  static var currentPencil = Pencil();

  static const pencilIcon = FontAwesomeIcons.pencil;
}
