package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.book.CustomBooks;

public class CustomBookCommand extends Command {

    public CustomBookCommand(String name) {
        super(name, "Выдать кастомную книгу", "/cbook <namespace:name> <уровень> [игрок]");
        this.setPermission("nukkit.command.cbook");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("args", CommandParamType.RAWTEXT, true)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(this.usageMessage);
            sender.sendMessage("Книги: " + CustomBooks.ids());
            return true;
        }
        Player target = args.length > 2 ? sender.getServer().getPlayerExact(args[2])
                : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage("§cИгрок не найден");
            return true;
        }
        Item book;
        try {
            book = CustomBooks.createBook(args[0], Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            book = null;
        }
        if (book == null) {
            sender.sendMessage("§cНеизвестная книга или неверный уровень. Книги: " + CustomBooks.ids());
        } else if (!target.getInventory().canAddItem(book)) {
            sender.sendMessage("§cИнвентарь игрока полон");
        } else {
            target.getInventory().addItem(book);
            sender.sendMessage("§aКнига выдана: " + target.getName());
        }
        return true;
    }
}
