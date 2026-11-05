import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../providers/auth_provider.dart';
import '../../repositories/seller_shop_repository.dart';
import '../../core/network/api_exception.dart';
import '../../widgets/seller_ui.dart';
import '../../widgets/ppee_ui.dart';

/// Registration form persists a PENDING shop to the backend.
class SellerSetupScreen extends StatefulWidget {
  const SellerSetupScreen({super.key});

  @override
  State<SellerSetupScreen> createState() => _SellerSetupScreenState();
}

class _SellerSetupScreenState extends State<SellerSetupScreen> {
  final _shopForm = GlobalKey<FormState>();
  final _contactForm = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _description = TextEditingController();
  final _contactName = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _pickupAddress = TextEditingController();
  String? _category;
  int _step = 0;
  bool _confirmed = false;
  bool _saving = false;
  bool _existingShop = false;
  bool _loadingShop = true;
  bool _shopLoadFailed = false;
  String? _resultStatus;
  String? _submissionError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadMyShop());
  }

  Future<void> _loadMyShop() async {
    try {
      final result = await context.read<SellerShopRepository>().mine();
      if (!mounted) return;
      _name.text = result['name']?.toString() ?? '';
      _description.text = result['description']?.toString() ?? '';
      _contactName.text = result['contactName']?.toString() ?? '';
      _phone.text = result['contactPhone']?.toString() ?? '';
      _email.text = result['contactEmail']?.toString() ?? '';
      _pickupAddress.text = result['pickupAddress']?.toString() ?? '';
      setState(() {
        _existingShop = true;
        _category = result['primaryCategory']?.toString();
        _resultStatus = result['status']?.toString();
      });
    } on ApiException catch (e) {
      if (e.statusCode != 404 && mounted) {
        setState(() {
          _shopLoadFailed = true;
          _submissionError = e.message;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _shopLoadFailed = true;
          _submissionError = 'Không tải được thông tin gian hàng. Vui lòng thử lại.';
        });
      }
    } finally {
      if (mounted) setState(() => _loadingShop = false);
    }
  }

  void _backToSeller() {
    if (Navigator.of(context).canPop()) {
      Navigator.pop(context);
    } else {
      Navigator.pushReplacementNamed(context, AppRoutes.seller);
    }
  }

  @override
  void dispose() {
    for (final c in [_name, _description, _contactName, _phone, _email, _pickupAddress]) {
      c.dispose();
    }
    super.dispose();
  }

  void _next() {
    if (_step == 0 && !_shopForm.currentState!.validate()) return;
    if (_step == 1) {
      if (!_contactForm.currentState!.validate()) return;
      if (!_confirmed) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: PpeeText('Vui lòng xác nhận đã kiểm tra thông tin bản nháp.')));
        return;
      }
    }
    if (_step < 2) setState(() => _step++);
  }

  Future<void> _submit() async {
    if (!context.read<AuthProvider>().isAuthenticated) {
      Navigator.pushNamed(context, AppRoutes.login);
      return;
    }
    setState(() { _saving = true; _submissionError = null; });
    try {
      final payload = {
        'name': _name.text.trim(),
        'primaryCategory': _category,
        'description': _description.text.trim(),
        'contactName': _contactName.text.trim(),
        'contactPhone': _phone.text.trim(),
        'contactEmail': _email.text.trim(),
        'pickupAddress': _pickupAddress.text.trim(),
      };
      final repository = context.read<SellerShopRepository>();
      final result = _existingShop ? await repository.update(payload) : await repository.apply(payload);
      if (!mounted) return;
      setState(() {
        _existingShop = true;
        _resultStatus = result['status']?.toString() ?? 'PENDING';
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() { _submissionError = e.message; });
    } catch (_) {
      if (!mounted) return;
      setState(() { _submissionError = 'Không thể gửi đăng ký. Kiểm tra kết nối backend.'; });
    } finally {
      if (mounted) setState(() { _saving = false; });
    }
  }

  String? _required(String? s) => s == null || s.trim().isEmpty
      ? 'Vui lòng nhập thông tin này' : null;

  String? _emailValidator(String? s) {
    if (_required(s) != null) return _required(s);
    final v = s!.trim();
    return RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(v)
        ? null : 'Email chưa đúng định dạng';
  }

  String? _phoneValidator(String? s) {
    if (_required(s) != null) return _required(s);
    final digits = s!.replaceAll(RegExp(r'\D'), '');
    return digits.length >= 9 && digits.length <= 11
        ? null : 'Vui lòng nhập số điện thoại hợp lệ';
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    return SellerScaffold(child: SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(18, 24, 18, 48),
      child: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 930),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          TextButton.icon(onPressed: _backToSeller,
            icon: const Icon(Icons.arrow_back_rounded), label: const PpeeText('Kênh Người Bán')),
          const SizedBox(height: 12),
          const SellerPill(label: 'Bắt đầu với PPee', icon: Icons.storefront_outlined),
          const SizedBox(height: 16),
          PpeeText('Thiết lập gian hàng', style: Theme.of(context).textTheme.displaySmall),
          const SizedBox(height: 8),
          const PpeeText('Chuẩn bị thông tin để tạo kênh bán hàng của riêng bạn.',
            style: TextStyle(color: AppColors.gray700, fontSize: 14)),
          const SizedBox(height: 18),
          if (!auth.isAuthenticated) const SellerInfoNotice(
            message: 'Vui lòng đăng nhập để đăng ký gian hàng.'),
          if (!auth.isAuthenticated) ...[
            const SizedBox(height: 8),
            Align(alignment: Alignment.centerLeft, child: TextButton.icon(
              onPressed: () => Navigator.pushNamed(context, AppRoutes.login),
              icon: const Icon(Icons.login_rounded),
              label: const PpeeText('Đăng nhập tài khoản PPee hiện có'),
            )),
          ],
          if (_existingShop) SellerInfoNotice(message: 'Gian hàng của bạn có trạng thái $_resultStatus. Bạn có thể cập nhật thông tin tại đây.'),
          const SizedBox(height: 23),
          _StepProgress(current: _step),
          const SizedBox(height: 19),
          SellerPanel(child: Padding(padding: const EdgeInsets.all(3), child:
            _step == 0 ? _shopDetails() : _step == 1 ? _contactDetails() : _review())),
          const SizedBox(height: 19),
          Wrap(spacing: 12, runSpacing: 10, children: [
            if (_step > 0)
              OutlinedButton.icon(onPressed: () => setState(() => _step--),
                icon: const Icon(Icons.arrow_back_rounded), label: const PpeeText('Quay lại')),
            if (_step < 2)
              FilledButton.icon(onPressed: _next,
                icon: const Icon(Icons.arrow_forward_rounded), label: const PpeeText('Tiếp tục')),
            if (_step == 2)
              FilledButton.icon(
                onPressed: _saving || _loadingShop || _shopLoadFailed ? null : _submit,
                icon: _saving ? const Icon(Icons.hourglass_top) : const Icon(Icons.send_outlined),
                label: PpeeText(_saving ? 'Đang lưu...' : _existingShop ? 'Cập nhật gian hàng' : 'Gửi đăng ký gian hàng')),
            if (_submissionError != null)
              PpeeNotice(message: _submissionError!, isError: true),
            if (_resultStatus != null) ...[
              PpeeText('Đã lưu thông tin. Trạng thái: $_resultStatus.',
                style: const TextStyle(color: Colors.green)),
              TextButton.icon(onPressed: _backToSeller,
                icon: const Icon(Icons.storefront_outlined),
                label: const PpeeText('Quay về Kênh Người Bán')),
            ],
          ]),

        ]),
      )),
    ));
  }

  Widget _shopDetails() => Form(
    key: _shopForm,
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _sectionTitle('01', 'Thông tin gian hàng', 'Đặt tên và mô tả shop của bạn'),
      const SizedBox(height: 25),
      PpeeFormField(
        controller: _name,
        label: 'Tên gian hàng', requiredField: true,
        maxLength: 60, textCapitalization: TextCapitalization.words,
        hint: 'Nhập tên gian hàng của bạn',
        prefixIcon: Icons.store_outlined,
        validator: (s) {
          if (_required(s) != null) return _required(s);
          return s!.trim().length < 3 ? 'Tên shop cần ít nhất 3 ký tự' : null;
        },
      ),
      const SizedBox(height: 17),
      const PpeeText('Ngành hàng chính  *', style: TextStyle(
        color: AppColors.black, fontSize: 13.5, fontWeight: FontWeight.w700)),
      const SizedBox(height: 9),
      DropdownButtonFormField<String>(
        key: ValueKey(_category),
        initialValue: _category,
        isExpanded: true,
        decoration: InputDecoration(hintText: tr(context, 'Chọn ngành hàng phù hợp'),
          prefixIcon: Icon(Icons.category_outlined)),
        items: const [
          'Điện tử & công nghệ', 'Thời trang', 'Sức khỏe & làm đẹp',
          'Nhà cửa & đời sống', 'Sách & văn phòng phẩm', 'Thể thao & du lịch',
          'Mẹ & bé', 'Thực phẩm', 'Ngành hàng khác',
        ].map((s) => DropdownMenuItem(value: s, child: PpeeText(s))).toList(),
        onChanged: (v) => setState(() => _category = v),
        validator: (v) => v == null ? 'Vui lòng chọn ngành hàng' : null,
      ),
      const SizedBox(height: 17),
      PpeeFormField(
        controller: _description,
        label: 'Giới thiệu gian hàng', maxLines: 4, maxLength: 220,
        hint: 'Chia sẻ điểm nổi bật, phong cách và sản phẩm chủ đạo...',
      ),
    ]),
  );

  Widget _contactDetails() => Form(
    key: _contactForm,
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _sectionTitle('02', 'Thông tin liên hệ',
        'Thông tin để PPee hỗ trợ giao nhận hàng hóa'),
      const SizedBox(height: 25),
      PpeeFormGrid(children: [
        PpeeFormField(
          controller: _contactName, label: 'Họ tên người liên hệ',
          requiredField: true, hint: 'Tên người phụ trách',
          prefixIcon: Icons.person_outline_rounded,
          textCapitalization: TextCapitalization.words,
          validator: _required,
        ),
        PpeeFormField(
          controller: _phone, label: 'Số điện thoại',
          requiredField: true, hint: 'Số điện thoại liên hệ',
          prefixIcon: Icons.call_outlined,
          keyboardType: TextInputType.phone, validator: _phoneValidator,
        ),
        PpeeFormField(
          controller: _email, label: 'Email liên hệ',
          requiredField: true, hint: 'email@example.com',
          prefixIcon: Icons.alternate_email_rounded,
          keyboardType: TextInputType.emailAddress, validator: _emailValidator,
        ),
        PpeeFormField(
          controller: _pickupAddress, label: 'Địa chỉ lấy hàng',
          requiredField: true, maxLines: 2,
          hint: 'Số nhà, tên đường, phường/xã, tỉnh/thành...',
          prefixIcon: Icons.location_on_outlined,
          validator: _required,
        ),
      ]),
      const SizedBox(height: 20),
      CheckboxListTile(
        value: _confirmed, onChanged: (v) => setState(() => _confirmed = v ?? false),
        contentPadding: EdgeInsets.zero,
        controlAffinity: ListTileControlAffinity.leading,
        title: const PpeeText('Tôi đã kiểm tra thông tin đăng ký.',
          style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: AppColors.black)),
        subtitle: const PpeeText('Đơn đăng ký cần ADMIN duyệt.', style: TextStyle(fontSize: 12)),
      ),
    ]),
  );

  Widget _review() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    _sectionTitle('03', 'Xem lại thông tin', 'Xác nhận trước khi gửi đăng ký'),
    const SizedBox(height: 18),
    const SellerInfoNotice(message: 'Sau khi gửi, gian hàng sẽ được xét duyệt trước khi bắt đầu bán hàng.'),
    const SizedBox(height: 22),
    _summary('Tên gian hàng', _name.text.trim()),
    _summary('Ngành hàng chính', _category ?? '—'),
    if (_description.text.trim().isNotEmpty)
      _summary('Giới thiệu', _description.text.trim()),
    const Divider(height: 26),
    _summary('Người liên hệ', _contactName.text.trim()),
    _summary('Số điện thoại', _phone.text.trim()),
    _summary('Email', _email.text.trim()),
    _summary('Địa chỉ lấy hàng', _pickupAddress.text.trim()),
  ]);

  Widget _summary(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 9),
    child: LayoutBuilder(builder: (context, c) => c.maxWidth < 460
      ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          PpeeText(label, style: const TextStyle(color: AppColors.gray700, fontSize: 12)),
          const SizedBox(height: 4),
          PpeeText(value, style: const TextStyle(fontWeight: FontWeight.w700)),
        ])
      : Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(width: 190, child: PpeeText(label,
            style: const TextStyle(color: AppColors.gray700, fontSize: 13))),
          Expanded(child: PpeeText(value,
            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14))),
        ])),
  );

  Widget _sectionTitle(String number, String title, String subtitle) => Row(children: [
    Container(width: 43, height: 43, alignment: Alignment.center,
      decoration: BoxDecoration(color: AppColors.primarySoft,
        borderRadius: BorderRadius.circular(12)),
      child: PpeeText(number, style: const TextStyle(color: AppColors.primary,
        fontWeight: FontWeight.w900, fontSize: 17))),
    const SizedBox(width: 13),
    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      PpeeText(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
      const SizedBox(height: 4),
      PpeeText(subtitle, style: const TextStyle(fontSize: 12,
        color: AppColors.gray700)),
    ])),
  ]);
}

class _StepProgress extends StatelessWidget {
  const _StepProgress({required this.current});
  final int current;

  @override
  Widget build(BuildContext context) => Row(children: [
    for (var i = 0; i < 3; i++) ...[
      if (i > 0) const SizedBox(width: 8),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(height: 5, decoration: BoxDecoration(
          color: i <= current ? AppColors.primary : AppColors.gray300,
          borderRadius: BorderRadius.circular(5))),
        const SizedBox(height: 8),
        PpeeText(['Gian hàng', 'Liên hệ', 'Xem lại'][i],
          style: TextStyle(color: i <= current ? AppColors.primaryDark : AppColors.gray700,
            fontSize: 12, fontWeight: FontWeight.w700)),
      ])),
    ],
  ]);
}
