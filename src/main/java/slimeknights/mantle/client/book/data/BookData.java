package slimeknights.mantle.client.book.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.BookLoader;
import slimeknights.mantle.client.book.BookTransformer;
import slimeknights.mantle.client.book.data.content.ContentError;
import slimeknights.mantle.client.book.data.element.ItemStackData;
import slimeknights.mantle.client.book.repository.BookRepository;
import slimeknights.mantle.client.gui.book.GuiBook;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@SideOnly(Side.CLIENT)
public class BookData implements IDataItem {

  public transient int unnamedSectionCounter = 0;
  public transient ArrayList<SectionData> sections = new ArrayList<>();
  public transient AppearanceData appearance = new AppearanceData();
  public transient ArrayList<ItemStackData.ItemLink> itemLinks = new ArrayList<>();
  public transient HashMap<String, String> strings = new HashMap<>();
  public transient FontRenderer fontRenderer;
  private transient boolean initialized = false;

  protected final transient ArrayList<BookTransformer> transformers = new ArrayList<>();

  private ArrayList<BookRepository> repositories;

  public BookData(BookRepository... repositories) {
    this.repositories = new ArrayList<>(Arrays.asList(repositories));
  }

  public void reset() {
    initialized = false;
  }

  @Override
  public void load() {
    if(initialized) {
      return;
    }
    Mantle.logger.info("Started loading book...");
    try {
      initialized = true;
      sections.clear();
      appearance = new AppearanceData();
      itemLinks.clear();

      for(BookRepository repo : repositories) {
        try {
          List<SectionData> repoContents = repo.getSections();
          sections.addAll(repoContents);

          for(SectionData section : repoContents) {
            section.source = repo;
          }
        } catch(Exception e) {
          SectionData error = new SectionData();
          error.name = "errorenous";
          PageData page = new PageData(true);
          page.name = "errorenous";
          page.content = new ContentError("Failed to load repository " + repo.toString() + ".", e);
          error.pages.add(page);
          sections.add(error);

          e.printStackTrace();
        }

        ResourceLocation appearanceLocation = repo.getResourceLocation("appearance.json");

        if(repo.resourceExists(appearanceLocation)) {
          try {
            appearance = BookLoader.GSON
                .fromJson(repo.resourceToString(repo.getResource(appearanceLocation)), AppearanceData.class);
          } catch(Exception e) {
            e.printStackTrace();
          }
        }

        appearance.load();

        ResourceLocation itemLinkLocation = repo.getResourceLocation("items.json");

        if(repo.resourceExists(itemLinkLocation)) {
          try {
            itemLinks = new ArrayList<>(Arrays.asList(BookLoader.GSON
                                                          .fromJson(repo.resourceToString(repo.getResource(itemLinkLocation)), ItemStackData.ItemLink[].class)));
          } catch(Exception e) {
            e.printStackTrace();
          }
        }

        ResourceLocation languageLocation = repo.getResourceLocation("language.lang");

        if(repo.resourceExists(languageLocation)) {
          try {
            BufferedReader br = new BufferedReader(new InputStreamReader(repo.getResource(languageLocation)
                                                                             .getInputStream(), "UTF-8"));

            String next = br.readLine();

            while(next != null) {
              if(!next.startsWith("//") && next.contains("=")) {
                String key = next.substring(0, next.indexOf('='));
                String value = next.substring(next.indexOf('=') + 1);

                strings.put(key, value);
              }

              next = br.readLine();
            }
          } catch(Exception ignored) {
          }
        }
      }

      for(int i = 0; i < sections.size(); i++){
        SectionData section = sections.get(i);
        if(section.name == null)
          continue;

        List<SectionData> matchingSections = sections.stream().filter(sect -> section.name.equalsIgnoreCase(sect.name)).collect(Collectors.toList());
        if(matchingSections.size() < 2){
          continue;
        }

        LinkedSectionData linkedSection = new LinkedSectionData();

          for(SectionData match : matchingSections){
            linkedSection.addSection(match);

            if(match != section){
              sections.remove(match);
            }
          }


        sections.set(i, linkedSection);
      }

      for(SectionData section : sections) {
        if(section.source == null) {
          section.source = BookRepository.DUMMY;
        }

        section.parent = this;
        section.load();
      }

      for(BookTransformer transformer : transformers) {
        transformer.transform(this);
      }

      // Loads orphaned sections, unless something went wrong, that would only be sections added by a transformer
      for(SectionData section : sections) {
        if(section.source == null) {
          section.source = BookRepository.DUMMY;
        }

        if(section.parent == null) {
          section.parent = this;
          section.load();
        }
      }
    } catch(Exception e) {
      this.sections.clear();
      SectionData section = new SectionData(true);
      section.name = "errorenous";
      PageData page = new PageData(true);
      page.name = "errorenous";
      page.content = new ContentError("Failed to load the book due to an unexpected error.", e);
      section.pages.add(page);
      this.sections.add(section);

      e.printStackTrace();
    }

    Mantle.logger.info("Finished loading book");
  }

  public SectionData findSection(String name) {
    return findSection(name, null);
  }

  public SectionData findSection(String name, GuiBook.AdvancementCache advancementCache) {
    for(SectionData section : sections) {
      section.update(advancementCache);

      if(section.name.equals(name.toLowerCase())) {
        return section.isUnlocked(advancementCache) ? section : null;
      }
    }

    return null;
  }

  public int getFirstPageNumber(SectionData section) {
    return getFirstPageNumber(section, null);
  }

  public int getFirstPageNumber(SectionData section, @Nullable GuiBook.AdvancementCache advancementCache) {
    int pages = 0;
    for(SectionData sect : sections) {
      sect.update(advancementCache);

      if(section == sect) {
        return section.isUnlocked(advancementCache) ? pages + 1 : -1;
      }

      if(!sect.isUnlocked(advancementCache)) {
        continue;
      }

      pages += sect.getPageCount();
    }

    return -1;
  }

  public PageData findPage(int number) {
    return findPage(number, null);
  }

  public PageData findPage(int number, @Nullable GuiBook.AdvancementCache advancementCache) {
    if(number < 0) {
      return null;
    }

    int pages = 0;
    for(SectionData section : sections) {
      section.update(advancementCache);

      if(!section.isUnlocked(advancementCache)) {
        continue;
      }

      if(pages + section.getPageCount() > number) {
        return section.pages.get(number - pages);
      }
      else {
        pages += section.getPageCount();
      }
    }

    return null;
  }

  public PageData findPage(String location) {
    return findPage(location, null);
  }

  public PageData findPage(String location, @Nullable GuiBook.AdvancementCache advancementCache) {
    return findPage(findPageNumber(location, advancementCache));
  }

  public int findPageNumber(String location) {
    return findPageNumber(location, null);
  }

  public int findPageNumber(String location, @Nullable GuiBook.AdvancementCache advancementCache) {
    location = location.toLowerCase();

    int pages = 0;

    if(!location.contains(".")) {
      return -1;
    }

    String sectionName = location.substring(0, location.indexOf('.'));
    String pageName = location.substring(location.indexOf('.') + 1);

    for(SectionData section : sections) {
      section.update(advancementCache);

      if(!section.isUnlocked(advancementCache)) {
        continue;
      }

      if(!sectionName.equals(section.name)) {
        pages += section.getPageCount();
        continue;
      }

      for(PageData page : section.pages) {
        if(!pageName.equals(page.name)) {
          pages++;
          continue;
        }

        return pages + 1;
      }
    }

    return -1;
  }

  public int getPageCount() {
    return getPageCount(null);
  }

  public int getPageCount(@Nullable GuiBook.AdvancementCache advancementCache) {
    int pages = 0;
    for(SectionData section : sections) {
      section.update(advancementCache);

      pages += section.isUnlocked(advancementCache) ? section.getPageCount() : 0;
    }
    return pages;
  }

  public int getFullPageCount() {
    return getFullPageCount(null);
  }

  public int getFullPageCount(@Nullable GuiBook.AdvancementCache advancementCache) {
    return (int) Math.ceil((getPageCount(advancementCache) - 1) / 2F) + 1;
  }

  public String getItemAction(ItemStackData item) {
    for(ItemStackData.ItemLink link : itemLinks) {
      if(item.id.equals(link.item.id) && (!link.damageSensitive || item.damage == link.item.damage)) {
        return link.action;
      }
    }

    return "";
  }

  public List<SectionData> getVisibleSections(GuiBook.AdvancementCache advancementCache) {
    List<SectionData> visible = new ArrayList<>();

    for(SectionData section : sections) {
      if(section.isUnlocked(advancementCache) || !section.hideWhenLocked) {
        visible.add(section);
      }
    }

    return visible;
  }


  public String translate(String string) {
    String out = strings.get(string);
    return out != null ? out : string;
  }

  public void openGui(@Nullable ItemStack item) {
    if(!initialized) {
      load();
    }
    if(Minecraft.getMinecraft().player != null) {
      Minecraft.getMinecraft().displayGuiScreen(new GuiBook(this, item));
    }
  }

  public void addRepository(BookRepository repository) {
    if(repository != null && !this.repositories.contains(repository)) {
      this.repositories.add(repository);
    }
  }

  public void addTransformer(BookTransformer transformer) {
    if(transformer != null && !transformers.contains(transformer)) {
      transformers.add(transformer);
    }
  }
}
