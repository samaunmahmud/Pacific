package com.pacific.marketplace.notify;

import com.pacific.marketplace.domain.Carriers;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.PaymentMethod;
import com.pacific.marketplace.domain.ShippingAddress;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the shop's emails as plain text plus an HTML version. Everything a customer or seller typed (names,
 * addresses, product names, tracking numbers) goes through HTML escaping.
 */
public class EmailTemplates {

    private static final String BRAND = "#860752";

    private final String baseUrl;
    private final String currency;

    public EmailTemplates(String publicUrl, String currency) {
        this.baseUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
        this.currency = currency == null ? "GBP" : currency;
    }

    // ---------- customer ----------

    /** One email for a whole checkout, even when it became several orders (one per seller). */
    public Email orderConfirmation(List<Order> orders) {
        Order first = orders.get(0);
        boolean card = PaymentMethod.CARD.name().equals(first.getPaymentMethod());
        String ids = ids(orders);
        Doc d = new Doc().heading("Thanks for your order, " + firstName(first) + "!");
        d.para(card
                ? "We've received your payment and your " + (orders.size() == 1 ? "order is" : "orders are")
                        + " confirmed. The seller will start preparing " + (orders.size() == 1 ? "it" : "them") + " now."
                : "Your " + (orders.size() == 1 ? "order is" : "orders are") + " confirmed. You'll pay when "
                        + (orders.size() == 1 ? "it arrives." : "they arrive."));
        BigDecimal grand = BigDecimal.ZERO;
        for (Order o : orders) {
            d.subheading("Order #" + o.getId() + " · sold by " + seller(o));
            items(d, o);
            grand = grand.add(o.getTotal());
        }
        if (orders.size() > 1) d.total("Total", money(grand));
        d.subheading("Delivering to");
        address(d, first.getAddress());
        d.subheading("Payment");
        d.para(card ? "Paid by card, " + money(grand) + "." : "Pay on delivery: " + money(grand) + " due when it arrives.");
        d.button("View your order" + (orders.size() == 1 ? "" : "s"),
                baseUrl + (orders.size() == 1 ? "/orders/" + first.getId() : "/orders"));
        String subject = orders.size() == 1 ? "Your Pacific order " + ids + " is confirmed"
                : "Your Pacific orders " + ids + " are confirmed";
        return d.build(first.getUser().getEmail(), subject, "ORDER_CONFIRMATION");
    }

    public Email orderShipped(Order o) {
        Doc d = new Doc().heading("Your order is on its way");
        d.para("Order #" + o.getId() + " from " + seller(o) + " has been sent.");
        String url = Carriers.trackingUrl(o.getTrackingCarrier(), o.getTrackingNumber());
        if (o.getTrackingNumber() != null) {
            d.subheading("Tracking");
            d.para((o.getTrackingCarrier() == null ? "Tracking number" : o.getTrackingCarrier()) + ": " + o.getTrackingNumber());
            if (url != null) d.button("Track your parcel", url);
        }
        items(d, o);
        d.button("View your order", baseUrl + "/orders/" + o.getId());
        return d.build(o.getUser().getEmail(), "Your Pacific order #" + o.getId() + " has shipped", "ORDER_SHIPPED");
    }

    public Email orderDelivered(Order o) {
        Doc d = new Doc().heading("Your order was delivered");
        d.para("Order #" + o.getId() + " from " + seller(o) + " has been delivered. We hope you love it.");
        d.para("Tell other shoppers what you think: your review helps them choose.");
        d.button("Review your items", baseUrl + "/account/reviews");
        d.para("If something isn't right, you can ask for a return from the order page.");
        d.button("View your order", baseUrl + "/orders/" + o.getId());
        return d.build(o.getUser().getEmail(), "Your Pacific order #" + o.getId() + " was delivered", "ORDER_DELIVERED");
    }

    /** by: who cancelled it ("you", "the seller", "Pacific"). refunded: money went back to the customer's card. */
    public Email orderCancelled(Order o, String by, BigDecimal refunded) {
        Doc d = new Doc().heading("Your order was cancelled");
        d.para("Order #" + o.getId() + " from " + seller(o) + " was cancelled by " + by + ".");
        d.para(refunded != null && refunded.signum() > 0
                ? money(refunded) + " has been refunded to your card. It can take 5 to 10 working days to show on your statement."
                : "You haven't been charged for this order.");
        items(d, o);
        d.button("Keep shopping", baseUrl + "/products");
        return d.build(o.getUser().getEmail(), "Your Pacific order #" + o.getId() + " was cancelled", "ORDER_CANCELLED");
    }

    // ---------- seller ----------

    public Email newOrderForSeller(Order o) {
        int units = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        Doc d = new Doc().heading("You have a new order");
        d.para("Order #" + o.getId() + " · " + units + " item" + (units == 1 ? "" : "s") + " · " + money(o.getTotal()));
        items(d, o);
        d.subheading("Send it to");
        address(d, o.getAddress());
        d.para(PaymentMethod.CARD.name().equals(o.getPaymentMethod())
                ? "The customer has paid by card."
                : "The customer will pay on delivery.");
        d.button("Manage this order", baseUrl + "/seller/orders");
        String to = o.getSeller().getUser().getEmail();
        return d.build(to, "New Pacific order #" + o.getId() + ": " + units + " item" + (units == 1 ? "" : "s")
                + ", " + money(o.getTotal()), "SELLER_NEW_ORDER");
    }

    // ---------- pieces ----------

    private void items(Doc d, Order o) {
        for (OrderItem i : o.getItems()) d.row(i.getQuantity() + " × " + i.getProductName(), money(i.lineTotal()));
        d.row("Delivery", o.getShipping().signum() == 0 ? "FREE" : money(o.getShipping()));
        d.total("Order total", money(o.getTotal()));
    }

    private static void address(Doc d, ShippingAddress a) {
        d.para(java.util.stream.Stream.of(a.getName(), a.getLine1(), a.getLine2(), a.getCity() + " " + a.getPostcode(), a.getCountry())
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining("\n")));
    }

    private static String seller(Order o) {
        return o.getSeller() == null ? "Pacific" : o.getSeller().getStoreName();
    }

    private static String firstName(Order o) {
        String name = o.getUser().getName();
        return name == null || name.isBlank() ? "there" : name.strip().split("\\s+")[0];
    }

    private static String ids(List<Order> orders) {
        return orders.stream().map(o -> "#" + o.getId()).collect(Collectors.joining(", "));
    }

    String money(BigDecimal amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.UK);
        f.setCurrency(Currency.getInstance(currency));
        return f.format(amount);
    }

    /** Builds the text and HTML versions side by side, so they can't drift apart. */
    private static final class Doc {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder html = new StringBuilder();

        Doc heading(String s) {
            text.append(s).append("\n\n");
            html.append("<h1 style=\"margin:0 0 12px;font-size:22px;color:#222\">").append(esc(s)).append("</h1>");
            return this;
        }

        Doc subheading(String s) {
            text.append('\n').append(s).append('\n');
            html.append("<h2 style=\"margin:22px 0 6px;font-size:15px;color:").append(BRAND).append("\">").append(esc(s)).append("</h2>");
            return this;
        }

        Doc para(String s) {
            text.append(s).append("\n");
            html.append("<p style=\"margin:0 0 10px;line-height:1.5\">").append(esc(s).replace("\n", "<br>")).append("</p>");
            return this;
        }

        Doc row(String left, String right) {
            text.append(left).append(": ").append(right).append('\n');
            html.append("<table width=\"100%\" style=\"border-collapse:collapse\"><tr><td style=\"padding:3px 0\">").append(esc(left))
                    .append("</td><td align=\"right\" style=\"padding:3px 0;white-space:nowrap\">").append(esc(right)).append("</td></tr></table>");
            return this;
        }

        Doc total(String left, String right) {
            text.append(left).append(": ").append(right).append('\n');
            html.append("<table width=\"100%\" style=\"border-collapse:collapse;border-top:1px solid #ddd;margin-top:4px\"><tr><td style=\"padding:6px 0;font-weight:bold\">")
                    .append(esc(left)).append("</td><td align=\"right\" style=\"padding:6px 0;font-weight:bold\">").append(esc(right)).append("</td></tr></table>");
            return this;
        }

        Doc button(String label, String url) {
            text.append('\n').append(label).append(": ").append(url).append('\n');
            html.append("<p style=\"margin:16px 0\"><a href=\"").append(esc(url)).append("\" style=\"background:").append(BRAND)
                    .append(";color:#fff;text-decoration:none;padding:10px 20px;border-radius:20px;display:inline-block;font-weight:bold\">")
                    .append(esc(label)).append("</a></p>");
            return this;
        }

        Email build(String to, String subject, String kind) {
            String textBody = text + "\n--\nPacific. This is an automated message about your account.\n";
            String htmlBody = "<!doctype html><html><body style=\"margin:0;background:#eaeded;font-family:Arial,Helvetica,sans-serif;color:#333\">"
                    + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:20px\">"
                    + "<table width=\"560\" style=\"max-width:100%;background:#fff;border-radius:8px;overflow:hidden\" cellpadding=\"0\" cellspacing=\"0\">"
                    + "<tr><td style=\"background:#37092a;color:#fff;padding:16px 24px;font-size:22px;font-family:Georgia,serif;font-weight:bold\">Pacific</td></tr>"
                    + "<tr><td style=\"padding:24px\">" + html + "</td></tr>"
                    + "<tr><td style=\"padding:14px 24px;background:#f6f6f6;color:#777;font-size:12px\">Pacific. This is an automated message about your account.</td></tr>"
                    + "</table></td></tr></table></body></html>";
            return new Email(to, subject, textBody, htmlBody, kind);
        }

        private static String esc(String s) {
            return HtmlUtils.htmlEscape(s);
        }
    }
}
