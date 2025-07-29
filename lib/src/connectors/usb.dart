import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_pos_printer_platform_image_3/discovery.dart';
import 'package:flutter_pos_printer_platform_image_3/flutter_pos_printer_platform_image_3.dart';

class UsbPrinterInput extends BasePrinterInput {
  final String? name;
  final String? vendorId;
  final String? productId;
  final String? deviceId;
  final String? deviceName; // الجديد لدعم التوافق

  UsbPrinterInput({
    this.name,
    this.vendorId,
    this.productId,
    this.deviceId,
    this.deviceName,
  });

  @override
  Map<String, dynamic> toJson() => {
        'name': name,
        'vendorId': vendorId,
        'productId': productId,
        'deviceId': deviceId,
        'deviceName': deviceName,
      };
}

class UsbPrinterConnector implements PrinterConnector<UsbPrinterInput> {
  UsbPrinterConnector._()
      : vendorId = '',
        productId = '',
        name = '',
        deviceId = '' {
    if (Platform.isAndroid) {
      flutterPrinterEventChannelUSB.receiveBroadcastStream().listen((data) {
        if (data is int) {
          _status = USBStatus.values[data];
          _statusStreamController.add(_status);
        }
      });
    }
  }

  static final UsbPrinterConnector _instance = UsbPrinterConnector._();
  static UsbPrinterConnector get instance => _instance;

  final StreamController<USBStatus> _statusStreamController =
      StreamController.broadcast();

  String vendorId;
  String productId;
  String deviceId;
  String name;
  USBStatus _status = USBStatus.none;

  USBStatus get status => _status;

  Stream<USBStatus> get currentStatus async* {
    if (Platform.isAndroid) {
      yield* _statusStreamController.stream;
    }
  }

  /// 🔍 Stream discovery for UI ListView
  Stream<PrinterDevice> discovery() async* {
    final List<dynamic> results =
        await flutterPrinterChannel.invokeMethod('getList');
    for (final r in results) {
      var name = (r['product'] ?? r['name']) ?? 'unknown device';
      yield PrinterDevice(
        name: name,
        vendorId: r['vendorId'],
        productId: r['productId'],
        address: r['deviceId'], // full device path
      );
    }
  }

  /// ✅ Optional: List-based discovery for one-time calls
  static Future<List<PrinterDiscovered<UsbPrinterInfo>>>
      discoverPrinters() async {
    final List<dynamic> results =
        await flutterPrinterChannel.invokeMethod('getList');
    return results.map((dynamic r) {
      return PrinterDiscovered<UsbPrinterInfo>(
        name: r['product'],
        detail: UsbPrinterInfo.Android(
          vendorId: r['vendorId'],
          productId: r['productId'],
          manufacturer: r['manufacturer'],
          product: r['product'],
          name: r['name'],
          deviceId: r['deviceId'],
        ),
      );
    }).toList();
  }

  Future<bool> _connect({UsbPrinterInput? model}) async {
    if (Platform.isAndroid) {
      // ✅ Always disconnect first to avoid locking USB device
      await _close();

      vendorId = model?.vendorId ?? vendorId;
      productId = model?.productId ?? productId;
      deviceId = model?.deviceId ?? deviceId;
      name = model?.name ?? name;

      final params = {
        "vendor": int.tryParse(vendorId) ?? 0,
        "product": int.tryParse(productId) ?? 0,
        "deviceId": deviceId,
        "deviceName":
            model?.deviceName, 
      };

      return await flutterPrinterChannel.invokeMethod('connectPrinter', params);
    } else if (Platform.isWindows) {
      name = model?.name ?? name;
      return await flutterPrinterChannel
              .invokeMethod('connectPrinter', {"name": name}) ==
          1;
    }
    return false;
  }

  Future<bool> _close() async {
    if (Platform.isWindows || Platform.isAndroid) {
      return await flutterPrinterChannel.invokeMethod('close') == 1;
    }
    return false;
  }

  @override
  Future<bool> connect(UsbPrinterInput model) async {
    try {
      // ✅ Always force disconnect first
      await _close();

      // ✅ Update device info
      vendorId = model.vendorId ?? '';
      productId = model.productId ?? '';
      deviceId = model.deviceId ?? '';
      name = model.name ?? '';

      return await _connect(model: model);
    } catch (e) {
      return false;
    }
  }

  @override
  Future<bool> disconnect({int? delayMs}) async {
    try {
      return await _close();
    } catch (e) {
      return false;
    }
  }

  @override
  Future<bool> send(List<int> bytes) async {
    try {
      final params = {
        "bytes": Platform.isWindows ? Uint8List.fromList(bytes) : bytes,
      };
      return await flutterPrinterChannel.invokeMethod('printBytes', params) ==
          1;
    } catch (e) {
      await _close();
      return false;
    }
  }
}

class UsbPrinterInfo {
  String vendorId;
  String productId;
  String manufacturer;
  String product;
  String name;
  String? model;
  bool isDefault = false;
  String deviceId;

  UsbPrinterInfo.Android({
    required this.vendorId,
    required this.productId,
    required this.manufacturer,
    required this.product,
    required this.name,
    required this.deviceId,
  });

  UsbPrinterInfo.Windows({
    required this.name,
    required this.model,
    required this.isDefault,
    this.vendorId = '',
    this.productId = '',
    this.manufacturer = '',
    this.product = '',
    this.deviceId = '',
  });
}
