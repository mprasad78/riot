/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.riotfamily.pages.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.hibernate.Session;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.IndexColumn;
import org.riotfamily.cachius.CacheService;
import org.riotfamily.common.hibernate.ActiveRecordBeanSupport;
import org.riotfamily.common.hibernate.Lifecycle;
import org.riotfamily.common.util.FormatUtils;
import org.riotfamily.common.util.Generics;
import org.riotfamily.components.model.ContentContainer;
import org.riotfamily.core.security.AccessController;
import org.riotfamily.website.cache.CacheTagUtils;
import org.riotfamily.website.cache.TagCacheItems;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.util.StringUtils;


/**
 * @author Felix Gnass [fgnass at neteye dot de]
 * @author Jan-Frederic Linde [jfl at neteye dot de]
 * @since 6.5
 */
@Entity
@Table(name="riot_pages", uniqueConstraints = {@UniqueConstraint(columnNames={"site_id", "path"})})
@Cache(usage=CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region="pages")
@TagCacheItems
public class ContentPage extends ActiveRecordBeanSupport implements Page, Lifecycle {

	public static final String TITLE_PROPERTY = "title";
	
	private String pageType;
	
	private ContentPage parent;
	
	private List<ContentPage> children;
	
	private ContentPage masterPage;
	
	private Set<ContentPage> translations;
	
	private Site site;

	private String pathComponent;

	private String path;
	
	private boolean published;

	private Date creationDate;

	private ContentContainer contentContainer;
	
	private CacheService cacheService;
	
	public ContentPage() {
	}

	public ContentPage(String pathComponent, Site site) {
		this.pathComponent = pathComponent;
		this.site = site;
	}

	public ContentPage(ContentPage master) {
		this.masterPage = master;
		this.pageType = master.getPageType();
		this.pathComponent = master.getPathComponent();
	}
	
	@Required	
	@Transient
	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}
	
	// ----------------------------------------------------------------------
	// Implementation of the SitemapItem interface
	// ----------------------------------------------------------------------
	
	public String getPathComponent() {
		return pathComponent;
	}
	
	public String getPageType() {
		return pageType;
	}

	@ManyToOne(cascade=CascadeType.MERGE)
	public Site getSite() {
		return this.site;
	}
	
	@ManyToOne(fetch=FetchType.LAZY, cascade=CascadeType.MERGE)
	@JoinColumn(name = "parent_id", updatable = false, insertable = false)
	public ContentPage getParent() {
		return parent;
	}
	
	@OneToMany
	@JoinColumn(name="parent_id")
	@IndexColumn(name="pos")
	@Cache(usage=CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region="pages")
	public List<ContentPage> getChildren() {
		return children;
	}
	
	@ManyToOne(cascade=CascadeType.ALL)
	public ContentContainer getContentContainer() {
		if (contentContainer == null) {
			contentContainer = new ContentContainer(this);
		}
		return contentContainer;
	}
	
	public boolean isPublished() {
		return this.published;
	}
	
	public void publish() {
		setPublished(true);
		invalidateCacheItems();
	}

	public void tag() {
		CacheTagUtils.tag(this);
	}
	
	// ----------------------------------------------------------------------
			
	public void setPageType(String pageType) {
		this.pageType = pageType;
	}

	public void setSite(Site site) {
		this.site = site;
	}
	
	public void setParent(ContentPage parent) {
		this.parent = parent;
	}
	
	public void setChildren(List<ContentPage> children) {
		this.children = children;
	}
	
	public void setPathComponent(String pathComponent) {
		this.pathComponent = pathComponent;
	}
	
	public void setContentContainer(ContentContainer contentContainer) {
		this.contentContainer = contentContainer;
	}
	
	// ----------------------------------------------------------------------
	
	@ManyToOne(cascade=CascadeType.MERGE)
	public ContentPage getMasterPage() {
		return masterPage;
	}

	public void setMasterPage(ContentPage masterPage) {
		this.masterPage = masterPage;
	}
		
	@OneToMany(mappedBy="masterPage", cascade={CascadeType.MERGE, CascadeType.PERSIST})
	@Cache(usage=CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region="pages")
	public Set<ContentPage> getTranslations() {
		return translations;
	}

	public void setTranslations(Set<ContentPage> translations) {
		this.translations = translations;
	}

	@Transient
	public Locale getLocale() {
		return site.getLocale();
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	@Transient
	public String getTitle() {
		return getTitle(true);
	}
	
	public String getTitle(boolean preview) {
		Object title = getContentContainer().getContent(preview).get(TITLE_PROPERTY);
		if (title != null) {
			return title.toString();
		}
		if (!StringUtils.hasText(pathComponent)) {
			return "/";
		}
		return FormatUtils.xmlToTitleCase(pathComponent);
	}
	
	public void setPublished(boolean published) {
		this.published = published;
	}

	@Transient
	public boolean isRequestable() {
		return (published && site.isEnabled())
			|| AccessController.isAuthenticatedUser();
	}
		
	@Transient
	public List<ContentPage> getSiblings() {
		if (parent == null) {
			return Collections.singletonList(this);
		}
		return parent.getChildren();
	}
	
	@Transient
	public Collection<ContentPage> getChildPagesWithFallback() {
		List<ContentPage> pages = Generics.newArrayList();
		pages.addAll(getChildren());
		pages.addAll(getTranslationCandidates());
		return pages;
	}
	
	@Transient
	public Collection<ContentPage> getTranslationCandidates() {
		List<ContentPage> candidates = Generics.newArrayList();
		ContentPage master = getMasterPage();
		if (master != null) {
			for (ContentPage page : master.getChildren()) {
				boolean translated = false;
				for (ContentPage child : getChildren()) {
					if (page.equals(child.getMasterPage())) {
						translated = true;
						break;
					}
				}
				if (!translated) {
					candidates.add(page);
				}
			}			
		}
		return candidates;
	}
	
	public String toString() {
		return site + ":" + path;
	}
	
	// ----------------------------------------------------------------------
	// 
	// ----------------------------------------------------------------------
	
	public void addPage(ContentPage child) {
		/*
		if (!PageValidationUtils.isValidChild(this, child)) {
			throw new DuplicatePathComponentException(
					"Page '{0}' did not validate", child.toString());
		}
		*/
		child.setParent(this);
		child.setSite(getSite());
		if (children == null) {
			children = Generics.newArrayList();
		}
		children.add(child);
		//deleteAlias(page);
		invalidateCacheItems();
	}
	
	public void removePage(ContentPage child) {
		children.remove(child);
		if (this.equals(child.getParent())) {
			child.setParent(null);
		}
	}
	
	public void unpublish() {
		setPublished(false);
		invalidateCacheItems();
	}
	
	private void invalidateCacheItems() {
		if (cacheService != null) {
			CacheTagUtils.invalidate(cacheService, this);
			if (getParent() != null) {
				CacheTagUtils.invalidate(cacheService, getParent());
			}
		}
	}
	
	// ----------------------------------------------------------------------
	// Materialized path methods
	// ----------------------------------------------------------------------
	
	public String getPath() {
		if (path == null) {
			materializePath();
		}
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
	private void updatePath() {
		if (materializePath()) {
			updateChildPaths();
		}
	}
	
	private void updateChildPaths() {
		if (children != null) {
			for (ContentPage child : children) {
				child.updatePath();
			}
		}
	}
	
	private boolean materializePath() {
		String path = buildPath();
		if (!path.equals(this.path)) {
			this.path = path;
			return true;
		}
		return false;
	}
	
	private String buildPath() {
		if (parent != null) {
			return parent.getPath() + "/" + pathComponent;
		}
		return "";
	}
	
	// ----------------------------------------------------------------------
	// Implementation of the Lifecycle interface
	// ----------------------------------------------------------------------
	
	public void onSave() {
		setCreationDate(new Date());
		updatePath();
	}
	
	public void onUpdate(Object oldState) {
		materializePath();
		updateChildPaths();
	}
	
	public void onDelete() {
	}
	
	// ----------------------------------------------------------------------
	// Persistence methods
	// ----------------------------------------------------------------------
	
	public static ContentPage load(Long id) {
		return load(ContentPage.class, id);
	}

	public void refreshIfDetached() {
		Session session = getSession();
		if (!session.contains(this)) {
			session.refresh(this);
		}
	}
	
	public static ContentPage loadBySiteAndPath(Site site, String path) {
		return load("from ContentPage where site = ? and path = ?", site, path);
	}
	
	public static ContentPage loadByTypeAndSite(String pageType, Site site) {
		return load("from ContentPage where pageType = ? and site = ?", pageType, site);
	}

}
