import React, { useState, useEffect } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

const BookDetails = () => {
  const { id } = useParams();
  const [book, setBook] = useState(null);
  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  useEffect(() => {
    const fetchBook = async () => {
      setLoading(true);
      try {
        const response = await axios.get(`${API_BASE_URL}/books/${id}/`);
        setBook(response.data);
      } catch (error) {
        console.error('Error fetching book:', error);
      }
      setLoading(false);
    };

    fetchBook();
  }, [id]);

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!book) {
    return <div className="error">Book not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{book.title_cn || book.title}</h1>
        <button className="btn-primary-blue" onClick={() => navigate(`${LIBRARY_PATH}/books`)}>Back to Books</button>

        <div className="details-grid">
          {book.thumb_image && (
            <div className="details-image">
              <img src={`${MEDIA_BASE_URL}/${book.thumb_image}`} alt={book.title_cn || book.title} style={{ maxWidth: '500px', height: 'auto' }} />
            </div>
          )}

          <div className="details-content">
            <h2>基本信息</h2>
            <p><strong>名称: </strong> {book.title}</p>
            {book.title_cn && <p><strong>中文名: </strong> {book.title_cn}</p>}
            {book.isbn && <p><strong>ISBN: </strong> {book.isbn}</p>}
            {book.authors && (
              <p><strong>作者: </strong> {
                book.authors.map((author, index) => (
                  <React.Fragment key={author?.id}>
                    <Link to={`${LIBRARY_PATH}/authors/${author?.id}`}>{author?.formated_name}</Link>
                    {index < book.authors.length - 1 && ', '}
                  </React.Fragment>
                ))
              }</p>
            )}
            {book.translators && <p><strong>翻译/编辑/整理: </strong> {book.translators.join(', ')}</p>}
            {book.publisher && <p><strong>出版社: </strong> 
              <React.Fragment key={book.publisher?.id}>
                <Link to={`${LIBRARY_PATH}/publishers/${book.publisher?.id}`}>{book.publisher?.name}</Link>
              </React.Fragment>
              </p>
            }
            {book.publish_date && <p><strong>出版日期: </strong> {new Date(book.publish_date).toLocaleDateString()}</p>}

            <h2>装帧信息</h2>
            {book.binding_type && <p><strong>装帧方式: </strong> {book.binding_type}</p>}
            {book.paper_type && <p><strong>正文用纸: </strong> {book.paper_type}</p>}
            {book.pages && <p><strong>页数: </strong> {book.pages}</p>}
            {book.book_count && <p><strong>册数: </strong> {book.book_count}</p>}
            {book.language && <p><strong>正文语言: </strong> {book.language}</p>}
            {book.compose_type && <p><strong>排版类型: </strong> {book.compose_type}</p>}

            <h2>购买信息</h2>
            {book.price && <p><strong>定价: </strong> ¥ {Number(book.price).toFixed(2)}</p>}
            <p><strong>购入价格: </strong> ¥ {Number(book.purchase_price).toFixed(2)}</p>
            {book.purchase_date && <p><strong>购入日期: </strong> {new Date(book.purchase_date).toLocaleDateString()}</p>}
            {book.purchase_store && <p><strong>来源: </strong> {book.purchase_store}</p>}

            <h2>出版信息</h2>
            {book.brand && <p><strong>出品方: </strong> 
              <React.Fragment key={book.brand?.id}>
                <Link to={`${LIBRARY_PATH}/brands/${book.brand?.id}`}>{book.brand?.name}</Link>
              </React.Fragment>
              </p>
            }
            {book.book_series && <p><strong>书系: </strong> 
              <React.Fragment key={book.book_series?.id}>
                <Link to={`${LIBRARY_PATH}/series/${book.book_series?.id}`}>{book.book_series?.name}</Link>
              </React.Fragment>
              </p>
            }
            {book.category && <p><strong>分类: </strong> 
              <React.Fragment key={book.category?.id}>
                <Link to={`${LIBRARY_PATH}/categories/${book.category?.id}`}>{book.category?.path}</Link>
              </React.Fragment>
              </p>
            }
            {book.bookshelf && <p><strong>所在书架: </strong> 
              <React.Fragment key={book.bookshelf?.id}>
                <Link to={`${LIBRARY_PATH}/bookshelves/${book.bookshelf?.id}`}>{book.bookshelf?.name}</Link>
              </React.Fragment>
              </p>
            }

            <h2>其他信息</h2>
            <p><strong>阅读状态: </strong> {book.read_state || 'Not set'}</p>
            <p><strong>登记状态: </strong> {book.registered ? 'Yes' : 'No'}</p>
            <p><strong>心愿单: </strong> {book.in_wish ? 'Yes' : 'No'}</p>
            {book.edition && <p><strong>版次: </strong> {book.edition}</p>}
            {book.print && <p><strong>印次: </strong> {book.print}</p>}
            {book.printed_number && <p><strong>印数: </strong> {book.printed_number}</p>}
            {book.douban_score && <p><strong>豆瓣评分: </strong> {book.douban_score}</p>}
            {book.tags && <p><strong>Tags:</strong> {book.tags.join(', ')}</p>}

            <h2>内容</h2>
            {book.introduction && <p><strong>内容简介: </strong> {book.introduction}</p>}
            {book.summary && <p><strong>内容概述: </strong> {book.summary}</p>}
            {book.catalog && <p><strong>目录: </strong> {book.catalog}</p>}
          </div>
        </div>
      </div>
    </section>
  );
};

export default BookDetails;
